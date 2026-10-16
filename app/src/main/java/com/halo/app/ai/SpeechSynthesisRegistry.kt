package com.halo.app.ai

import android.util.Log
import android.os.SystemClock
import com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect

/** Independent of inference routing. Resolves exactly once for each spoken response. */
class SpeechSynthesisRegistry(
    val providers: List<PcmSpeechSynthesisProvider>,
    val selector: SpeechProviderSelector,
    private val storage: Storage,
) {
    fun pin(request: SpeechRequest, explicitPreviewProviderId: String? = null,
        requiredProviderId: String? = null,
        localOnly: Boolean = false): Pair<PcmSpeechSynthesisProvider, SpeechRequest> {
        val android = providers.first { it.metadata.id == "provider.android.tts" }
        val liveSelection = selector.ttsSelection.value.id
        require(requiredProviderId == null || requiredProviderId == liveSelection) {
            "Voice provider changed before the confirmed reading could begin"
        }
        val id = explicitPreviewProviderId ?: requiredProviderId ?: liveSelection
        val configured = if (id == "AUTOMATIC") android else providers.firstOrNull { it.metadata.id == id }
            ?: error("Selected speech provider is unavailable")
        // Private material is never silently handed to a network voice, even if
        // an account's general assistant TTS preference is cloud-based.
        val selected = if(localOnly && !configured.metadata.local) android else configured
        // The only path around audible qualification is an explicit user-initiated
        // provider preview. Normal production turns never receive this flag.
        val previewRequest = if (explicitPreviewProviderId == com.halo.app.audio.SupertonicSpeechProvider.ID)
            request.copy(voiceSettings=request.voiceSettings + ("_supertonicPreview" to 1.0)) else request
        val pinned = (selected as? SegmentedPcmSpeechProvider)?.pinSpeechRequest(previewRequest)
            ?: (selected as? ElevenLabsTtsProvider)?.pin(previewRequest) ?: previewRequest
        val allowFallback = explicitPreviewProviderId == null && storage.getBoolean(FALLBACK, true)
        Log.i("HALO_TTS_PROVIDER_RESOLVED", "requested=$id; effective=${selected.metadata.id}; fallbackAllowed=$allowFallback")
        var fallbackUsed = false
        var hasProducedPcm = false
        val wrapper = object : SegmentedPcmSpeechProvider, SpeechSynthesisProvider by selected {
            override fun pinSpeechRequest(request: SpeechRequest) = pinned.copy(text=request.text)
            override fun streamPcm(request: SpeechRequest) = flow {
                val segmented=selected as? SegmentedPcmSpeechProvider
                if(segmented==null || fallbackUsed) {
                    synthesizeToPcm(request)?.let { emit(it) }
                } else {
                    try {
                        segmented.streamPcm(pinned.copy(text=request.text)).collect { pcm ->
                            hasProducedPcm=true; emit(pcm)
                        }
                    } catch(cancelled: CancellationException) { throw cancelled }
                    catch(error: Exception) {
                        if(hasProducedPcm || !allowFallback || selected===android) throw error
                        fallbackUsed=true
                        android.synthesizeToPcm(request.copy(voiceId=null,modelId=null,voiceSettings=emptyMap()))
                            ?.let { hasProducedPcm=true; emit(it) } ?: throw error
                    }
                }
            }
            override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech? {
                val startedAt = SystemClock.elapsedRealtime()
                val pcm = try {
                    if (!fallbackUsed && selected is ElevenLabsTtsProvider && (pinned.voiceId == null || pinned.modelId == null)) error("Speech selection is incomplete")
                    if (fallbackUsed) android.synthesizeToPcm(request.copy(voiceId = null, modelId = null, voiceSettings = emptyMap())) else selected.synthesizeToPcm(pinned.copy(text = request.text)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    val code = (error as? SpeechProviderException)?.code?.name
                        ?: error::class.java.simpleName
                    Log.w(
                        "HALO_TTS_FAILURE",
                        "provider=${selected.metadata.id}; stage=SYNTHESIS; code=$code",
                    )
                    null
                }
                if (pcm != null) {
                    hasProducedPcm = true
                    Log.i("HALO_TTS_SYNTHESIS", "provider=${if (fallbackUsed) android.metadata.id else selected.metadata.id}; audioBytes=${pcm.chunks.sumOf { it.size.toLong() }}; sampleRate=${pcm.sampleRateHz}; durationMs=${SystemClock.elapsedRealtime() - startedAt}")
                    return pcm
                }
                if (selected !== android && allowFallback && !hasProducedPcm && !fallbackUsed) {
                    Log.i("HALO_TTS_PROVIDER_FALLBACK", "from=${selected.metadata.id}; to=${android.metadata.id}; reason=synthesis_failed")
                    fallbackUsed = true
                    val fallbackPcm = android.synthesizeToPcm(request.copy(voiceId = null, modelId = null, voiceSettings = emptyMap()))
                    hasProducedPcm = fallbackPcm != null
                    return fallbackPcm
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
