package com.halo.drivers.heycyan.codec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class HeyCyanStructuralDecoderTest {

    private val decoder = HeyCyanStructuralDecoder()
    private val charId = UUID.randomUUID()

    @Test
    fun `BC 73 0E swipe examples are decoded correctly`() {
        val vectors = listOf(
            "BC730E005B6C120100100102000F0F0300100A03" to 0x01,
            "BC730E00AB63120100100202000F0F0300100A03" to 0x02,
            "BC730E00FAA6120100100302000F0F0300100A03" to 0x03,
            "BC730E004B7C120100100402000F0F0300100A03" to 0x04,
            "BC730E00BB73120100100702000F0F0300100A03" to 0x07,
            "BC730E008B43120100100802000F0F0300100A03" to 0x08,
            "BC730E00DA86120100100902000F0F0300100A03" to 0x09
        )

        vectors.forEach { (hex, action) ->
            val bytes = hex.decodeHex()
            val frame = RawHeyCyanFrame(100L, PacketDirection.RX, charId, bytes)
            val decoded = decoder.decode(frame)

            assertEquals(HeyCyanPacketFamily.BC73_0E, decoded.family)
            assertEquals(20, decoded.raw.bytes.size)
            assertArrayEquals(bytes, decoded.raw.bytes)
            assertEquals(action, decoded.fields["candidateAction"])
            assertEquals(0x03, decoded.fields["unknown19"])
        }
    }

    @Test
    fun `panel double tap is decoded correctly`() {
        val hex = "BC730E009B78120100100502000F0F0300100A01"
        val bytes = hex.decodeHex()
        val frame = RawHeyCyanFrame(100L, PacketDirection.RX, charId, bytes)
        val decoded = decoder.decode(frame)

        assertEquals(HeyCyanPacketFamily.BC73_0E, decoded.family)
        assertEquals(0x05, decoded.fields["candidateAction"])
        assertEquals(0x01, decoded.fields["unknown19"])
    }

    @Test
    fun `photo BC 73 08 is recognized`() {
        val hex = "BC7308000AC70107000500030001"
        val bytes = hex.decodeHex()
        val frame = RawHeyCyanFrame(100L, PacketDirection.RX, charId, bytes)
        val decoded = decoder.decode(frame)

        assertEquals(HeyCyanPacketFamily.BC73_08, decoded.family)
        assertArrayEquals(bytes, decoded.raw.bytes)
    }

    @Test
    fun `AI BC 73 08 is recognized`() {
        val hex = "BC730800FF060107000600040001"
        val bytes = hex.decodeHex()
        val frame = RawHeyCyanFrame(100L, PacketDirection.RX, charId, bytes)
        val decoded = decoder.decode(frame)

        assertEquals(HeyCyanPacketFamily.BC73_08, decoded.family)
    }

    @Test
    fun `AI double press BC 73 05 is recognized`() {
        val hex = "BC7305009D6602001C0200"
        val bytes = hex.decodeHex()
        val frame = RawHeyCyanFrame(100L, PacketDirection.RX, charId, bytes)
        val decoded = decoder.decode(frame)

        assertEquals(HeyCyanPacketFamily.BC73_05, decoded.family)
    }

    @Test
    fun `BC 73 02 events are recognized`() {
        val hexes = listOf("BC73020047590B23", "BC730200069B0B24")
        hexes.forEach { hex ->
            val bytes = hex.decodeHex()
            val frame = RawHeyCyanFrame(100L, PacketDirection.RX, charId, bytes)
            val decoded = decoder.decode(frame)
            assertEquals(HeyCyanPacketFamily.BC73_02, decoded.family)
        }
    }

    @Test
    fun `BC 59 28 stream frame is recognized`() {
        val hex = "BC59280060074B410D8030A0CC" // Truncated for test
        val bytes = hex.decodeHex()
        val frame = RawHeyCyanFrame(100L, PacketDirection.RX, charId, bytes)
        val decoded = decoder.decode(frame)

        assertEquals(HeyCyanPacketFamily.BC59_28, decoded.family)
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
