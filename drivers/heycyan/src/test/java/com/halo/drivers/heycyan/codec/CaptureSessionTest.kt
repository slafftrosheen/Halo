package com.halo.drivers.heycyan.codec

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.UUID

class CaptureSessionTest {
    private val characteristic = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val service = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val descriptor = UUID.fromString("00000000-0000-0000-0000-000000000003")

    @Test
    fun recorderPersistsGattMetadataPacketsAndTimeline() {
        val directory = Files.createTempDirectory("heycyan-capture").toFile()
        val store = JsonCaptureSessionStore(directory)
        var timestamp = 100L
        val recorder = CaptureSessionRecorder(
            store = store,
            now = { timestamp++ },
            captureIdFactory = { "button-press-001" }
        )

        recorder.start(
            scenario = "button-press",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "HeyCyan",
            metadata = CaptureMetadata(
                haloVersion = "1.2.3",
                gitCommit = "abc123",
                androidVersion = "Android 15 (API 35)",
                phoneModel = "Example Phone",
                bluetoothAdapter = "Example Adapter",
                glassesFirmware = "firmware-unknown"
            )
        )
        recorder.onMtuRequested(517)
        recorder.onConnectionStateChanged(status = 0, newState = 2)
        recorder.onDiscoveredService(service, serviceType = 0)
        recorder.onDiscoveredCharacteristic(service, characteristic, properties = 0x10, permissions = 0x01)
        recorder.onDiscoveredDescriptor(service, characteristic, descriptor, permissions = 0x02)
        recorder.onMtuChanged(247)
        recorder.recordAdvertisement(
            CaptureAdvertisement(
                timestamp = 105L,
                address = "AA:BB:CC:DD:EE:FF",
                name = "HeyCyan",
                rssi = -42,
                serviceUuids = listOf(service.toString()),
                manufacturerData = mapOf("76" to "00FF")
            )
        )
        recorder.onRx(characteristic, byteArrayOf(0x00, 0xFF.toByte()))
        recorder.onReady()
        val file = recorder.stop()

        assertNotNull(file)
        val capture = store.load(requireNotNull(file))
        assertEquals("button-press", capture.scenario)
        assertEquals("AA:BB:CC:DD:EE:FF", capture.deviceAddress)
        assertEquals("1.2.3", capture.metadata.haloVersion)
        assertEquals("abc123", capture.metadata.gitCommit)
        assertEquals("Android 15 (API 35)", capture.metadata.androidVersion)
        assertEquals("Example Phone", capture.metadata.phoneModel)
        assertEquals("Example Adapter", capture.metadata.bluetoothAdapter)
        assertEquals("firmware-unknown", capture.metadata.glassesFirmware)
        assertEquals(517, capture.requestedMtu)
        assertEquals(247, capture.mtu)
        assertEquals("HeyCyan", capture.advertisements.single().name)
        assertEquals("00FF", capture.advertisements.single().manufacturerData["76"])
        assertEquals(1, capture.services.size)
        assertEquals(1, capture.services.single().characteristics.single().descriptors.size)
        assertArrayEquals(byteArrayOf(0x00, 0xFF.toByte()), capture.packets.single().payload)
        assertEquals(0L, capture.packets.single().sequence)
        assertEquals(CaptureEventKind.SESSION_STARTED, capture.timeline.first().kind)
        assertEquals(CaptureEventKind.SESSION_FINISHED, capture.timeline.last().kind)
        assertTrue(capture.timeline.zipWithNext().all { (left, right) -> right.sequence > left.sequence })
    }

    @Test
    fun replayEmitsMetadataAndPacketsInRecordedOrder() = runTest {
        val directory = Files.createTempDirectory("heycyan-replay").toFile()
        val store = JsonCaptureSessionStore(directory)
        val recorder = CaptureSessionRecorder(
            store = store,
            now = object : () -> Long {
                private var value = 1_000L
                override fun invoke(): Long = value++
            },
            captureIdFactory = { "replay-001" }
        )
        recorder.start()
        recorder.onTx(characteristic, byteArrayOf(0x01))
        recorder.onRx(characteristic, byteArrayOf(0x02))
        val capture = store.load(requireNotNull(recorder.stop()))

        val replayed = mutableListOf<ReplayedCaptureEvent>()
        CaptureReplayPlayer().replay(capture, fastForward = true) { replayed += it }

        assertEquals(capture.timeline.size, replayed.size)
        assertEquals(
            capture.timeline.map { it.sequence },
            replayed.map { it.event.sequence }
        )
        val packets = replayed.mapNotNull { it.packet }
        assertEquals(2, packets.size)
        assertArrayEquals(byteArrayOf(0x01), packets[0].payload)
        assertArrayEquals(byteArrayOf(0x02), packets[1].payload)
        assertEquals(listOf(0L, 1L), packets.map { it.sequence })
    }

    @Test
    fun recorderDoesNotRetainMutablePacketPayload() {
        val directory = Files.createTempDirectory("heycyan-copy").toFile()
        val store = JsonCaptureSessionStore(directory)
        val recorder = CaptureSessionRecorder(store, captureIdFactory = { "copy-001" })
        val payload = byteArrayOf(0x01)

        recorder.start()
        recorder.onRx(characteristic, payload)
        payload[0] = 0x7F
        val capture = recorder.snapshot()

        assertArrayEquals(byteArrayOf(0x01), requireNotNull(capture).packets.single().payload)
        recorder.stop()
    }

    @Test
    fun recorderUsesExplicitScenarioCaptureIdWhenProvided() {
        val directory = Files.createTempDirectory("heycyan-explicit-id").toFile()
        val store = JsonCaptureSessionStore(directory)
        val recorder = CaptureSessionRecorder(store, captureIdFactory = { "generated-id" })

        recorder.start(
            scenario = "button-single-001",
            captureId = "button-single-001"
        )
        val file = recorder.stop()

        assertEquals("heycyan-button-single-001.json", requireNotNull(file).name)
        assertEquals("button-single-001", store.load(requireNotNull(file)).captureId)
    }

    @Test
    fun storeRefusesToOverwriteDifferentEvidenceWithSameCaptureId() {
        val directory = Files.createTempDirectory("heycyan-immutable-save").toFile()
        val store = JsonCaptureSessionStore(directory)
        store.save(
            BleCapture(
                captureId = "boot-001",
                startedAt = 100L,
                scenario = "boot-001"
            )
        )

        try {
            store.save(
                BleCapture(
                    captureId = "boot-001",
                    startedAt = 200L,
                    scenario = "boot-001"
                )
            )
            throw AssertionError("Expected duplicate capture ID to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: immutable capture IDs cannot be reused for different evidence.
        }
    }

    @Test
    fun legacyCaptureWithoutPacketSequencesOrMetadataRemainsLoadable() {
        val directory = Files.createTempDirectory("heycyan-legacy").toFile()
        val file = directory.resolve("legacy.json")
        file.writeText(
            """
            {
              "schemaVersion": 1,
              "captureId": "legacy-001",
              "startedAt": 100,
              "scenario": "discovery",
              "advertisements": [],
              "packets": [{
                "timestamp": 101,
                "direction": "RX",
                "characteristic": "$characteristic",
                "payload": "00FF"
              }],
              "services": [],
              "timeline": []
            }
            """.trimIndent()
        )

        val capture = JsonCaptureSessionStore(directory).load(file)

        assertEquals(1, capture.schemaVersion)
        assertEquals(CaptureMetadata(), capture.metadata)
        assertEquals(0L, capture.packets.single().sequence)
        assertNull(capture.deviceAddress)
    }
}