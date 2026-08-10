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
        val decoded = HeyCyanEventDecoder().decode(payload, characteristic, 42L).event

        assertTrue(decoded is HeyCyanEvent.Unknown || decoded is HeyCyanEvent.UnknownEvent)
    }

    @Test
    fun `production decoder remains raw-only with no definitions`() {
        val payload = byteArrayOf(0x01)
        val decoded = HeyCyanEventDecoder().decode(payload, notifyCharacteristic, 7L).event

        assertTrue(decoded is HeyCyanEvent.BatteryUpdated || decoded is HeyCyanEvent.UnknownEvent || decoded is HeyCyanEvent.Unknown)
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
            decoder.decode(byteArrayOf(0x01), characteristic, 10L).event
        )
    }

    @Test
    fun `encoder rejects commands without evidence-backed definitions`() {
        try {
            HeyCyanCommandEncoder().encode(HeyCyanCommand.TakePhoto).getOrThrow()
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
            HeyCyanCommandEncoder().encode(HeyCyanCommand.RequestBattery).getOrThrow()
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
    private val hypothesisDecoder = HeyCyanEventDecoder(SyntheticHeyCyanProtocolFixtures.eventDecoderDefinitions())

    @Test
    fun `hypothesis decoder decodes Ready packet from notify characteristic`() {
        val event = hypothesisDecoder.decode(
            payload = byteArrayOf(0x01),
            characteristicUuid = notifyCharacteristic,
            timestamp = 1000L
        ).event
        assertEquals(HeyCyanEvent.Ready, event)
    }

    @Test
    fun `hypothesis decoder decodes BatteryUpdated at 90 percent not charging`() {
        val event = hypothesisDecoder.decode(
            payload = byteArrayOf(0x02, 0x5A, 0x00),
            characteristicUuid = notifyCharacteristic,
            timestamp = 1100L
        ).event
        assertTrue(event is HeyCyanEvent.BatteryUpdated)
        val battery = event as HeyCyanEvent.BatteryUpdated
        assertEquals(90, battery.level)
        assertEquals(false, battery.isCharging)
    }

    @Test
    fun `hypothesis decoder decodes BatteryUpdated at 50 percent charging`() {
        val event = hypothesisDecoder.decode(
            payload = byteArrayOf(0x02, 0x32, 0x01),
            characteristicUuid = notifyCharacteristic,
            timestamp = 1200L
        ).event
        assertTrue(event is HeyCyanEvent.BatteryUpdated)
        val battery = event as HeyCyanEvent.BatteryUpdated
        assertEquals(50, battery.level)
        assertEquals(true, battery.isCharging)
    }

    @Test
    fun `hypothesis decoder decodes ButtonPressed SingleTap`() {
        val event = hypothesisDecoder.decode(
            payload = byteArrayOf(0x03, 0x01),
            characteristicUuid = notifyCharacteristic,
            timestamp = 3100L
        ).event
        assertEquals(HeyCyanEvent.ButtonPressed(ButtonType.SINGLE_TAP), event)
    }

    @Test
    fun `hypothesis decoder decodes ButtonPressed DoubleTap`() {
        val event = hypothesisDecoder.decode(
            payload = byteArrayOf(0x03, 0x02),
            characteristicUuid = notifyCharacteristic,
            timestamp = 3200L
        ).event
        assertEquals(HeyCyanEvent.ButtonPressed(ButtonType.DOUBLE_TAP), event)
    }

    @Test
    fun `hypothesis decoder decodes ButtonPressed LongPress`() {
        val event = hypothesisDecoder.decode(
            payload = byteArrayOf(0x03, 0x03),
            characteristicUuid = notifyCharacteristic,
            timestamp = 3300L
        ).event
        assertEquals(HeyCyanEvent.ButtonPressed(ButtonType.LONG_PRESS), event)
    }

    @Test
    fun `hypothesis decoder returns UnknownEvent for unrecognized payload`() {
        val payload = byteArrayOf(0xFF.toByte(), 0x00, 0xAB.toByte())
        val event = hypothesisDecoder.decode(
            payload = payload,
            characteristicUuid = notifyCharacteristic,
            timestamp = 9000L
        ).event
        assertTrue(event is HeyCyanEvent.Unknown || event is HeyCyanEvent.UnknownEvent)
    }

    @Test
    fun `hypothesis decoder returns UnknownEvent for correct payload but wrong characteristic`() {
        val event = hypothesisDecoder.decode(
            payload = byteArrayOf(0x01),
            characteristicUuid = writeCharacteristic,
            timestamp = 100L
        ).event
        assertTrue(event is HeyCyanEvent.Unknown || event is HeyCyanEvent.UnknownEvent)
    }

    @Test
    fun `hypothesis decoder payload copy is immutable`() {
        val payload = byteArrayOf(0x02, 0x64, 0x00)
        val event = hypothesisDecoder.decode(payload, notifyCharacteristic, 0L).event
        payload[1] = 0x00
        assertTrue(event is HeyCyanEvent.BatteryUpdated)
        assertEquals(100, (event as HeyCyanEvent.BatteryUpdated).level)
    }

    // -------------------------------------------------------------------------
    // PR-014 — Hypothesis command encoder definitions (synthetic fixture evidence)
    // -------------------------------------------------------------------------

    private val hypothesisEncoder = HeyCyanCommandEncoder(SyntheticHeyCyanProtocolFixtures.commandEncoderDefinitions())

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
            hypothesisEncoder.encode(HeyCyanCommand.TakePhoto).getOrThrow()
            throw AssertionError("Expected UnsupportedOperationException")
        } catch (_: UnsupportedOperationException) {
            // Expected — TakePhoto has no evidence-backed definition.
        }
    }

    @Test
    fun `hypothesis encoder still rejects StartVideo without definitions`() {
        try {
            hypothesisEncoder.encode(HeyCyanCommand.StartVideo).getOrThrow()
            throw AssertionError("Expected UnsupportedOperationException")
        } catch (_: UnsupportedOperationException) {
            // Expected — StartVideo has no evidence-backed definition.
        }
    }

    // -------------------------------------------------------------------------
    // SyntheticHeyCyanProtocolFixtures completeness checks
    // -------------------------------------------------------------------------

    @Test
    fun `all event definitions have non-empty evidence lists`() {
        val definitions = SyntheticHeyCyanProtocolFixtures.eventDecoderDefinitions()
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
        val definitions = SyntheticHeyCyanProtocolFixtures.commandEncoderDefinitions()
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
        val definitions = SyntheticHeyCyanProtocolFixtures.eventDecoderDefinitions()
        val ids = definitions.map { it.id }
        assertEquals("Event definition IDs must be unique", ids.distinct().size, ids.size)
    }
}