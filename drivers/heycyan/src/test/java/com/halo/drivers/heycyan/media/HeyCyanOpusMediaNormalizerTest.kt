package com.halo.drivers.heycyan.media

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class HeyCyanOpusMediaNormalizerTest {

    @Test
    fun `already Ogg bytes are left unchanged`() {
        val oggBytes = "OggSfake_stream_data".toByteArray(Charsets.US_ASCII)
        assertTrue(HeyCyanOpusMediaNormalizer.isAlreadyOgg(oggBytes))
        val normalized = HeyCyanOpusMediaNormalizer.normalize(oggBytes)
        assertArrayEquals(oggBytes, normalized)
    }

    @Test
    fun `fixed 40-byte packets are wrapped into valid Ogg Opus container`() {
        // Create 5 back-to-back 40-byte packets (200 bytes)
        val raw = ByteArray(200) { (it % 250).toByte() }
        assertFalse(HeyCyanOpusMediaNormalizer.isAlreadyOgg(raw))

        val normalized = HeyCyanOpusMediaNormalizer.normalize(raw)
        assertTrue(HeyCyanOpusMediaNormalizer.isAlreadyOgg(normalized))

        // Verify Ogg magic header
        assertEquals('O'.code.toByte(), normalized[0])
        assertEquals('g'.code.toByte(), normalized[1])
        assertEquals('g'.code.toByte(), normalized[2])
        assertEquals('S'.code.toByte(), normalized[3])

        // Verify contains OpusHead and OpusTags
        val normalizedAscii = String(normalized, Charsets.US_ASCII)
        assertTrue(normalizedAscii.contains("OpusHead"))
        assertTrue(normalizedAscii.contains("OpusTags"))
        assertTrue(normalizedAscii.contains("Halo"))
    }

    @Test
    fun `length prefixed packets are wrapped into valid Ogg Opus container`() {
        // 3 packets of length 50 prefixed by 2-byte LE length
        val bos = ByteArrayOutputStream()
        for (i in 0 until 3) {
            val len = 50
            bos.write(len and 0xFF)
            bos.write((len shr 8) and 0xFF)
            bos.write(ByteArray(len) { i.toByte() })
        }
        val raw = bos.toByteArray()
        val normalized = HeyCyanOpusMediaNormalizer.normalize(raw)
        assertTrue(HeyCyanOpusMediaNormalizer.isAlreadyOgg(normalized))
        val ascii = String(normalized, Charsets.US_ASCII)
        assertTrue(ascii.contains("OpusHead"))
    }

    @Test
    fun `empty or unparseable input returned safely`() {
        val empty = ByteArray(0)
        assertEquals(0, HeyCyanOpusMediaNormalizer.normalize(empty).size)

        val oddBytes = byteArrayOf(0x01, 0x02, 0x03)
        assertArrayEquals(oddBytes, HeyCyanOpusMediaNormalizer.normalize(oddBytes))
    }
}
