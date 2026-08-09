package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.ButtonType
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class HeyCyanCodecTest {
    private val characteristic = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val evidence = listOf(PacketEvidenceReference("capture-001", packetSequence = 4L))

    // -------------------------------------------------------------------------
    // Baseline codec contract tests (pre-PR-013)
    // -------------------------------------------------------------------------

    @Test
    fun `decoder retains unknown packet bytes instead of returning null`() {
        val payload = byteArrayOf(0x00, 0x7F, 0xFF.toByte())
        val decoded = HeyCyanEventDecoder().decode(payload, characteristic, 42L)

        assertTrue(decoded is HeyCyanEvent.UnknownEvent)
        val unknown = decoded as HeyCyanEvent.UnknownEvent
        assertArrayEquals(payload, unknown.payload)
        assertEquals(characteristic, unknown.characteristic)
        assertEquals(42L, unknown.timestamp)

        payload[0] = 0x55
        assertEquals(0x00, unknown.payload[0].toInt())
    }

    @Test
    fun `production decoder remains raw-only with no definitions`() {
        val payload = byteArrayOf(0x01)
        val decoded = HeyCyanEventDecoder().decode(payload, notifyCharacteristic, 7L)

        assertTrue(decoded is HeyCyanEvent.UnknownEvent)
        assertArrayEquals(payload, (decoded as HeyCyanEvent.UnknownEvent).payload)
    }

    @Test
    fun `decoder accepts only definitions with evidence`() {
        val decoder = HeyCyanEventDecoder(
            definitions = listOf(
                HeyCyanEventDecoder.Definition(
                    id = "ready-001",
                    evidence = evidence,
                    matches = { payload, uuid -> uuid == characteristic && payload.contentEquals(byteArrayOf(0x01)) },
                    decode = { _, _, _ -> HeyCyanEvent.Ready }
                )
            )
        )

        assertEquals(
            HeyCyanEvent.Ready,
            decoder.decode(byteArrayOf(0x01), characteristic, 10L)
        )
    }

    @Test
    fun `encoder rejects commands without evidence-backed definitions`() {
        try {
            HeyCyanCommandEncoder().encode(HeyCyanCommand.TakePhoto)
            throw AssertionError("Expected unsupported command to fail")
        } catch (_: UnsupportedOperationException) {
            // Expected.
        }
    }

    @Test
    fun `encoder copies evidence-backed payload`() {
        val payload = byteArrayOf(0x10, 0x20)
        val encoder = HeyCyanCommandEncoder(
            definitions = listOf(
                HeyCyanCommandEncoder.Definition(
                    command = HeyCyanCommand.TakePhoto,
                    characteristic = characteristic,
                    payload = payload,
                    evidence = evidence
                )
            )
        )

        val encoded = encoder.encodeWithTarget(HeyCyanCommand.TakePhoto)
        payload[0] = 0x7F
        assertArrayEquals(byteArrayOf(0x10, 0x20), encoded.payload)
        assertEquals(characteristic, encoded.characteristic)
        assertEquals(evidence, encoded.evidence)
    }

    @Test
    fun `production command encoder exposes no speculative commands`() {
        try {
            HeyCyanCommandEncoder().encode(HeyCyanCommand.RequestBattery)
            throw AssertionError("Expected speculative command to fail")
        } catch (_: UnsupportedOperationException) {
            // Expected: discovery mode has no command definitions.
        }
    }

    // -------------------------------------------------------------------------
    // PR-013 — Hypothesis decoder definitions (synthetic fixture evidence)
    // -------------------------------------------------------------------------

    private val notifyCharacteristic = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private val writeCharacteristic = UUID.fromString("0000abce-0000-1000-8000-00805f9b34fb")
    private val hypothesisDecoder = HeyCyanEventDecoder(HeyCyanProtocolDefinitions.eventDecoderDefinitions())

    @Test
    fun `hypothesis decoder decodes Ready packet from notify characteristic`() {
        val event = hypothesisDecoder.decode(
            payload = byteArrayOf(0x01),
            characteristic = notifyCharacteristic,
            timestamp = 1000L
        )
        assertEquals(HeyCyanEvent.Ready, event)
    }

    @Test
    fun `hypothesis decoder decodes BatteryUpdated at 90 percent not charging`() {
        val event = hypothesisDecoder.decode(
            payload = byteArrayOf(0x02, 0x5A, 0x00),
            characteristic = notifyCharacteristic,
            timestamp = 1100L
        )
        assertTrue(event is HeyCyanEvent.BatteryUpdated)
        val battery = event as HeyCyanEvent.BatteryUpdated
        assertEquals(90, battery.level)
        assertEquals(false, battery.isCharging)
    }

    @Test
    fun `hypothesis decoder decodes BatteryUpdated at 50 percent charging`() {
        val event = hypothesisDecoder.decode(
            payload = byteArrayOf(0x02, 0x32, 0x01),
            characteristic = notifyCharacteristic,
            timestamp = 1200L
        )
        assertTrue(event is HeyCyanEvent.BatteryUpdated)
        val battery = event as HeyCyanEvent.BatteryUpdated
        assertEquals(50, battery.level)
        assertEquals(true, battery.isCharging)
    }

    @Test
    fun `hypothesis decoder decodes ButtonPressed SingleTap`() {
        val event = hypothesisDecoder.decode(
            payload = byteArrayOf(0x03, 0x01),
            characteristic = notifyCharacteristic,
            timestamp = 3100L
        )
        assertEquals(HeyCyanEvent.ButtonPressed(ButtonType.SINGLE_TAP), event)
    }

    @Test
    fun `hypothesis decoder decodes ButtonPressed DoubleTap`() {
        val event = hypothesisDecoder.decode(
            payload = byteArrayOf(0x03, 0x02),
            characteristic = notifyCharacteristic,
            timestamp = 3200L
        )
        assertEquals(HeyCyanEvent.ButtonPressed(ButtonType.DOUBLE_TAP), event)
    }

    @Test
    fun `hypothesis decoder decodes ButtonPressed LongPress`() {
        val event = hypothesisDecoder.decode(
            payload = byteArrayOf(0x03, 0x03),
            characteristic = notifyCharacteristic,
            timestamp = 3300L
        )
        assertEquals(HeyCyanEvent.ButtonPressed(ButtonType.LONG_PRESS), event)
    }

    @Test
    fun `hypothesis decoder returns UnknownEvent for unrecognized payload`() {
        val payload = byteArrayOf(0xFF.toByte(), 0x00, 0xAB.toByte())
        val event = hypothesisDecoder.decode(
            payload = payload,
            characteristic = notifyCharacteristic,
            timestamp = 9000L
        )
        assertTrue(event is HeyCyanEvent.UnknownEvent)
        val unknown = event as HeyCyanEvent.UnknownEvent
        assertArrayEquals(payload, unknown.payload)
    }

    @Test
    fun `hypothesis decoder returns UnknownEvent for correct payload but wrong characteristic`() {
        val event = hypothesisDecoder.decode(
            payload = byteArrayOf(0x01),
            characteristic = writeCharacteristic,
            timestamp = 100L
        )
        assertTrue(event is HeyCyanEvent.UnknownEvent)
    }

    @Test
    fun `hypothesis decoder payload copy is immutable`() {
        val payload = byteArrayOf(0x02, 0x64, 0x00)
        val event = hypothesisDecoder.decode(payload, notifyCharacteristic, 0L)
        payload[1] = 0x00
        assertTrue(event is HeyCyanEvent.BatteryUpdated)
        assertEquals(100, (event as HeyCyanEvent.BatteryUpdated).level)
    }

    // -------------------------------------------------------------------------
    // PR-014 — Hypothesis command encoder definitions (synthetic fixture evidence)
    // -------------------------------------------------------------------------

    private val hypothesisEncoder = HeyCyanCommandEncoder(HeyCyanProtocolDefinitions.commandEncoderDefinitions())

    @Test
    fun `hypothesis encoder encodes RequestBattery to write characteristic`() {
        val encoded = hypothesisEncoder.encodeWithTarget(HeyCyanCommand.RequestBattery)
        assertArrayEquals(byteArrayOf(0x0A.toByte()), encoded.payload)
        assertEquals(writeCharacteristic, encoded.characteristic)
        assertTrue(encoded.evidence.isNotEmpty())
    }

    @Test
    fun `hypothesis encoder payload copy is immutable`() {
        val encoded = hypothesisEncoder.encodeWithTarget(HeyCyanCommand.RequestBattery)
        val copy1 = encoded.copyPayload()
        val copy2 = encoded.copyPayload()
        copy1[0] = 0x7F
        assertEquals(0x0A.toByte(), copy2[0])
    }

    @Test
    fun `hypothesis encoder still rejects commands without definitions`() {
        try {
            hypothesisEncoder.encode(HeyCyanCommand.TakePhoto)
            throw AssertionError("Expected UnsupportedOperationException")
        } catch (_: UnsupportedOperationException) {
            // Expected — TakePhoto has no evidence-backed definition.
        }
    }

    @Test
    fun `hypothesis encoder still rejects StartVideo without definitions`() {
        try {
            hypothesisEncoder.encode(HeyCyanCommand.StartVideo)
            throw AssertionError("Expected UnsupportedOperationException")
        } catch (_: UnsupportedOperationException) {
            // Expected — StartVideo has no evidence-backed definition.
        }
    }

    // -------------------------------------------------------------------------
    // HeyCyanProtocolDefinitions completeness checks
    // -------------------------------------------------------------------------

    @Test
    fun `all event definitions have non-empty evidence lists`() {
        val definitions = HeyCyanProtocolDefinitions.eventDecoderDefinitions()
        assertTrue("Expected at least one event definition", definitions.isNotEmpty())
        definitions.forEach { definition ->
            assertTrue(
                "Definition ${definition.id} has no evidence",
                definition.evidence.isNotEmpty()
            )
        }
    }

    @Test
    fun `all command definitions have non-empty evidence lists`() {
        val definitions = HeyCyanProtocolDefinitions.commandEncoderDefinitions()
        assertTrue("Expected at least one command definition", definitions.isNotEmpty())
        definitions.forEach { definition ->
            assertTrue(
                "Command ${definition.command} has no evidence",
                definition.evidence.isNotEmpty()
            )
        }
    }

    @Test
    fun `all event definition IDs are unique`() {
        val definitions = HeyCyanProtocolDefinitions.eventDecoderDefinitions()
        val ids = definitions.map { it.id }
        assertEquals("Event definition IDs must be unique", ids.distinct().size, ids.size)
    }
}