package com.halo.app.ai

import android.util.Log
import android.os.SystemClock
import com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException

/** Independent of inference routing. Resolves exactly once for each spoken response. */
class SpeechSynthesisRegistry(
    val providers: List<PcmSpeechSynthesisProvider>,
    val selector: SpeechProviderSelector,
    private val storage: Storage,
) {
    fun pin(request: SpeechRequest, explicitPreviewProviderId: String? = null): Pair<PcmSpeechSynthesisProvider, SpeechRequest> {
        val android = providers.first { it.metadata.id == "provider.android.tts" }
        val id = explicitPreviewProviderId ?: selector.ttsSelection.value.id
        val selected = if (id == "AUTOMATIC") android else providers.firstOrNull { it.metadata.id == id }
            ?: error("Selected speech provider is unavailable")
        val pinned = (selected as? ElevenLabsTtsProvider)?.pin(request) ?: request
        val allowFallback = explicitPreviewProviderId == null && storage.getBoolean(FALLBACK, true)
        Log.i("HALO_TTS_PROVIDER_RESOLVED", "requested=$id; effective=${selected.metadata.id}; fallbackAllowed=$allowFallback")
        var fallbackUsed = false
        val wrapper = object : PcmSpeechSynthesisProvider, SpeechSynthesisProvider by selected {
            override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech? {
                val startedAt = SystemClock.elapsedRealtime()
                val pcm = try {
                    if (!fallbackUsed && selected is ElevenLabsTtsProvider && (pinned.voiceId == null || pinned.modelId == null)) error("Speech selection is incomplete")
                    if (fallbackUsed) android.synthesizeToPcm(request.copy(voiceId = null, modelId = null, voiceSettings = emptyMap())) else selected.synthesizeToPcm(pinned.copy(text = request.text)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                if (pcm != null) {
                    Log.i("HALO_TTS_SYNTHESIS", "provider=${if (fallbackUsed) android.metadata.id else selected.metadata.id}; audioBytes=${pcm.chunks.sumOf { it.size.toLong() }}; sampleRate=${pcm.sampleRateHz}; durationMs=${SystemClock.elapsedRealtime() - startedAt}")
                    return pcm
                }
                if (selected !== android && allowFallback) {
                    Log.i("HALO_TTS_PROVIDER_FALLBACK", "from=${selected.metadata.id}; to=${android.metadata.id}; reason=synthesis_failed")
                    fallbackUsed = true
                    return android.synthesizeToPcm(request.copy(voiceId = null, modelId = null, voiceSettings = emptyMap()))
                }
                return null
            }
        }
        return wrapper to pinned
    }
    fun stopCurrent() = providers.forEach { it.stopCurrent() }
    suspend fun close() = providers.forEach { it.close() }
    companion object { const val FALLBACK = "tts_cloud_android_fallback" }
}
