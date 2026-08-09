package com.halo.drivers.heycyan.codec

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
}