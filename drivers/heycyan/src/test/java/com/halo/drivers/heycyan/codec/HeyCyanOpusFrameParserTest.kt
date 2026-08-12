package com.halo.drivers.heycyan.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeyCyanOpusFrameParserTest {

    @Test
    fun testParse_capturedBC59_4B41_invalidRFC() {
        val parser = HeyCyanOpusFrameParser()
        
        // This is the raw 40-byte payload observed in BC59 28 (excluding BC59 28 00 CRC)
        val rawPayloadStr = "4B 41 1E 0B E4 C1 22 23 61 F0 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
        val payload = rawPayloadStr.split(" ").map { it.toInt(16).toByte() }.toByteArray()
        
        val result = parser.parse(payload)
        
        assertEquals("TOC byte", 0x4B, result.tocByte)
        assertEquals("Config (0x4B -> 9)", 9, result.config)
        assertEquals("Code 3", 3, result.code)
        assertEquals("v bit from 0x41", 1, result.v)
        assertEquals("p bit from 0x41", 0, result.p)
        assertEquals("M (frame count) from 0x41", 16, result.frameCountM)
        
        // Expected padding is 0 because p=0
        assertEquals("Padding length", 0, result.paddingLength)
        
        // Is RFC valid? No, because M=16 for 20ms frames exceeds 120ms max.
        assertFalse("Should be invalid RFC6716 Code 3", result.isRfcValid)
        assertTrue(result.invalidReason?.contains("exceeds 120ms max duration limit") == true)
    }
}
