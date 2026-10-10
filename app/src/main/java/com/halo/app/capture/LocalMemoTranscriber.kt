package com.halo.app.capture

import android.net.Uri
import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.provider.BufferedSpeechRecognitionProvider
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext

/** Uses the selected offline recognizer, not LLM audio capabilities or remote fallback. */
class LocalMemoTranscriber(
    private val decoder: MemoPcmDecoder,
    private val provider: () -> BufferedSpeechRecognitionProvider?,
    val readiness: kotlinx.coroutines.flow.StateFlow<com.halo.providers.sherpa.stt.RecognitionReadiness>,
) {
    suspend fun transcribe(uri: Uri, objectId: String): String {
        val selected = provider()
        check(selected is com.halo.providers.sherpa.stt.ReloadableSpeechRecognitionProvider && selected.health()) {
            "Local speech model unavailable. Choose an installed model in Voice settings or write a transcript."
        }
        val decoded = decoder.decode(uri, "memo:$objectId")
        val text = buildString {
            for ((index, frame) in decoded.audio.frames.withIndex()) {
                currentCoroutineContext().ensureActive()
                val result = selected.recognize(AudioBuffer(listOf(frame), "memo:$objectId:$index"))
                if (result.text.isNotBlank()) { if (isNotEmpty()) append('\n'); append(result.text.trim()) }
                check(length <= 12_000) { "Transcript too long. Split the recording." }
            }
        }
        check(text.isNotBlank()) { "No recognizable speech. Write a transcript or retry with a clearer recording." }
        return text
    }
}
