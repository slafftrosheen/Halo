package com.halo.app.di.tools

import com.halo.app.audio.*
import com.halo.sdk.runtime.Storage
import org.junit.Assert.*
import org.junit.Test

class VoiceSettingsOwnerParityTest {
    @Test fun guardedVoiceExperienceUpdatePreservesConcurrentUiWinnerAndRestoresAllFields() {
        val storage = MemoryStorage()
        val owner = VoiceExperienceSettingsRepository(storage)
        val initial = owner.state.value
        val uiWinner = initial.copy(wearablePhoneFallback = true, wearNearbyInteraction = false)
        owner.update(uiWinner)
        assertTrue(runCatching { owner.updateExpected(initial, initial.copy(streamingSpeechEnabled = false)) }.isFailure)
        assertEquals(uiWinner, VoiceExperienceSettingsRepository(storage).state.value)
        owner.updateExpected(uiWinner, uiWinner.copy(streamingSpeechEnabled = false))
        assertFalse(owner.readPersisted().streamingSpeechEnabled)
        assertTrue(owner.readPersisted().wearablePhoneFallback)
    }
    @Test fun localVoiceChoiceRequiresExactPackAndBoundedIdAndPersistsBothUiKeys() {
        val storage = MemoryStorage()
        LocalKokoroVoiceSelection.select(storage, "pack", 11, 10)
        assertEquals("10", storage.getString("kokoro_voice"))
        assertEquals("10", storage.getString("kokoro_voice.pack"))
        assertTrue(runCatching { LocalKokoroVoiceSelection.select(storage, "pack", 11, 11) }.isFailure)
        assertEquals("10", storage.getString("kokoro_voice"))
    }
    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
