package com.halo.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Catalog acceptance is not equivalent to installed bytes or device qualification. */
class LocalVoiceStarterCatalogTest {
    @Test fun supertonicIsSupportedMultilingualLocalPack() {
        val pack = SpeechPackRegistry.supertonic
        assertFalse(pack.experimental)
        assertEquals("3", pack.version)
        assertEquals(SupertonicSpeechProvider.ID, pack.providerId)
        assertEquals(setOf("en", "lv", "ru", "pl"), pack.languages)
        assertEquals(10, pack.voices.size)
        assertTrue(pack.voices.all { it.languages == pack.languages })
        assertTrue(pack.archiveSha256?.matches(Regex("[0-9a-f]{64}")) == true)
    }

    @Test fun kokoroMultilingualOnePointOneRemainsSupportedAndLanguageBounded() {
        val pack = SpeechPackRegistry.multilingual
        assertFalse(pack.experimental)
        assertEquals("1.1", pack.version)
        assertEquals(KokoroSpeechProvider.ID, pack.providerId)
        assertEquals(setOf("en", "zh"), pack.languages)
        assertEquals(103, pack.voices.size)
        assertFalse("Kokoro must not advertise Latvian", "lv" in pack.languages)
    }
}
