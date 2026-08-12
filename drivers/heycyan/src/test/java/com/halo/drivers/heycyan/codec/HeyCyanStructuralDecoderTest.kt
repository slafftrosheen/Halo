package com.halo.drivers.heycyan.codec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class HeyCyanStructuralDecoderTest {

    private val decoder = HeyCyanStructuralDecoder()
    private val charId = UUID.randomUUID()

    @Test
    fun `BC 73 0E swipe examples are decoded with action 0x73 and payload length 14`() {
        val vectors = listOf(
            "BC730E005B6C120100100102000F0F0300100A03",
            "BC730E00AB63120100100202000F0F0300100A03",
            "BC730E00FAA6120100100302000F0F0300100A03",
            "BC730E004B7C120100100402000F0F0300100A03",
            "BC730E00BB73120100100702000F0F0300100A03",
            "BC730E008B43120100100802000F0F0300100A03",
            "BC730E00DA86120100100902000F0F0300100A03"
        )

        vectors.forEach { hex ->
            val bytes = hex.decodeHex()
            val frame = RawHeyCyanFrame(100L, PacketDirection.RX, charId, bytes)
            val decoded = decoder.decode(frame)

            assertEquals(HeyCyanActionFamily.DEVICE_DATA_REPORTING, decoded.actionFamily)
            assertEquals(0x73.toByte(), decoded.action)
            assertEquals(14, decoded.payloadLength)
            assertEquals(14, decoded.payload.size)
            assertTrue(decoded.isValidCrc)
            assertArrayEquals(bytes, decoded.raw.bytes)
        }
    }

    @Test
    fun `panel double tap is decoded with action 0x73 and valid CRC`() {
        val hex = "BC730E009B78120100100502000F0F0300100A01"
        val bytes = hex.decodeHex()
        val frame = RawHeyCyanFrame(100L, PacketDirection.RX, charId, bytes)
        val decoded = decoder.decode(frame)

        assertEquals(HeyCyanActionFamily.DEVICE_DATA_REPORTING, decoded.actionFamily)
        assertEquals(14, decoded.payloadLength)
        assertEquals(0x789B, decoded.crc16)
        assertTrue(decoded.isValidCrc)
    }

    @Test
    fun `photo BC 73 08 reporting frame is recognized with action 0x73 and length 8`() {
        val hex = "BC7308000AC70107000500030001"
        val bytes = hex.decodeHex()
        val frame = RawHeyCyanFrame(100L, PacketDirection.RX, charId, bytes)
        val decoded = decoder.decode(frame)

        assertEquals(HeyCyanActionFamily.DEVICE_DATA_REPORTING, decoded.actionFamily)
        assertEquals(8, decoded.payloadLength)
        assertEquals(0xC70A, decoded.crc16)
        assertTrue(decoded.isValidCrc)
    }

    @Test
    fun `AI BC 73 08 reporting frame is recognized with action 0x73 and length 8`() {
        val hex = "BC730800FF060107000600040001"
        val bytes = hex.decodeHex()
        val frame = RawHeyCyanFrame(100L, PacketDirection.RX, charId, bytes)
        val decoded = decoder.decode(frame)

        assertEquals(HeyCyanActionFamily.DEVICE_DATA_REPORTING, decoded.actionFamily)
        assertEquals(8, decoded.payloadLength)
        assertEquals(0x06FF, decoded.crc16)
        assertTrue(decoded.isValidCrc)
    }

    @Test
    fun `AI double press BC 73 05 reporting frame is recognized with action 0x73 and length 5`() {
        val hex = "BC7305009D6602001C0200"
        val bytes = hex.decodeHex()
        val frame = RawHeyCyanFrame(100L, PacketDirection.RX, charId, bytes)
        val decoded = decoder.decode(frame)

        assertEquals(HeyCyanActionFamily.DEVICE_DATA_REPORTING, decoded.actionFamily)
        assertEquals(5, decoded.payloadLength)
        assertEquals(0x669D, decoded.crc16)
        assertTrue(decoded.isValidCrc)
    }

    @Test
    fun `BC 73 02 reporting frames are recognized with action 0x73 and length 2`() {
        val hexes = listOf("BC73020047590B23", "BC730200069B0B24")
        hexes.forEach { hex ->
            val bytes = hex.decodeHex()
            val frame = RawHeyCyanFrame(100L, PacketDirection.RX, charId, bytes)
            val decoded = decoder.decode(frame)
            assertEquals(HeyCyanActionFamily.DEVICE_DATA_REPORTING, decoded.actionFamily)
            assertEquals(2, decoded.payloadLength)
            assertTrue(decoded.isValidCrc)
        }
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
