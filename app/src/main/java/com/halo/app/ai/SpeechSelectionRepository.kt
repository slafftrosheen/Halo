package com.halo.app.ai

import com.halo.sdk.runtime.Storage

data class PersistedSpeechSelection(
    val sttProviderId: String,
    val ttsProviderId: String
)

class SpeechSelectionRepository(private val storage: Storage) {
    fun readSelection(): PersistedSpeechSelection {
        val stt = storage.getString(STT_KEY)?.takeIf { it.isNotBlank() } ?: "AUTOMATIC"
        val tts = storage.getString(TTS_KEY)?.takeIf { it.isNotBlank() } ?: "AUTOMATIC"
        return PersistedSpeechSelection(stt, tts)
    }

    fun writeSelection(selection: PersistedSpeechSelection) {
        storage.putBatch(
            strings = mapOf(
                STT_KEY to selection.sttProviderId,
                TTS_KEY to selection.ttsProviderId
            )
        )
    }

    companion object {
        const val STT_KEY = "speech_stt_provider"
        const val TTS_KEY = "speech_tts_provider"
    }
}
