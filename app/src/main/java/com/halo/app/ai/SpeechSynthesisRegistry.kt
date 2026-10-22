package com.halo.app.ai

import android.util.Log
import android.os.SystemClock
import com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider
import com.halo.app.audio.SupertonicSpeechProvider
import com.halo.app.audio.KokoroSpeechProvider
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect

/** Independent of inference routing. Resolves exactly once for each spoken response. */
class SpeechSynthesisRegistry(
    val providers: List<PcmSpeechSynthesisProvider>,
    val selector: SpeechProviderSelector,
    private val storage: Storage,
    private val allowCloudSpeech: () -> Boolean = { true },
) {
    private val stopEpoch = java.util.concurrent.atomic.AtomicLong()
    fun pin(request: SpeechRequest, explicitPreviewProviderId: String? = null,
        requiredProviderId: String? = null,
        localOnly: Boolean = false): Pair<PcmSpeechSynthesisProvider, SpeechRequest> {
        val epoch = stopEpoch.get()
        val privateOnly = localOnly || !allowCloudSpeech()
        val android = providers.first { it.metadata.id == "provider.android.tts" }
        val liveSelection = selector.ttsSelection.value.id
        require(requiredProviderId == null || requiredProviderId == liveSelection) {
            "Voice provider changed before the confirmed reading could begin"
        }
        val id = explicitPreviewProviderId ?: requiredProviderId ?: liveSelection
        // Automatic is local-first, never a cloud voice. Preserve explicit selections.
        // Approval is per installed Supertonic artifact + language + voice, not global.
        val automatic = providers.filterIsInstance<SupertonicSpeechProvider>()
            .firstOrNull { it.supportsApprovedSpeech(request) }
            ?: providers.filterIsInstance<KokoroSpeechProvider>()
                .firstOrNull { it.canUseForSpeech(request.language) }
            ?: android
        val configured = if (id == "AUTOMATIC") automatic else providers.firstOrNull { it.metadata.id == id }
            ?: error("Selected speech provider is unavailable")
        // Private material is never silently handed to a network voice, even if
        // an account's general assistant TTS preference is cloud-based.
        if (explicitPreviewProviderId != null && !allowCloudSpeech() && !configured.metadata.local)
            error("Cloud voice needs Power mode. Use the switch at the top of Halo. Kids sessions also need grown-up approval.")
        val selected = if(privateOnly && !configured.metadata.local) android else configured
        // The only path around audible qualification is an explicit user-initiated
        // provider preview. Normal production turns never receive this flag.
        val safeRequest = if(privateOnly && selected === android)
            request.copy(voiceSettings=request.voiceSettings + ("_haloOfflineOnly" to 1.0))
        else request
        val previewRequest = if (explicitPreviewProviderId == com.halo.app.audio.SupertonicSpeechProvider.ID)
            safeRequest.copy(voiceSettings=safeRequest.voiceSettings + ("_supertonicPreview" to 1.0))
        else safeRequest
        val pinned = (selected as? SegmentedPcmSpeechProvider)?.pinSpeechRequest(previewRequest)
            ?: (selected as? ElevenLabsTtsProvider)?.pin(previewRequest) ?: previewRequest
        val allowFallback = explicitPreviewProviderId == null && storage.getBoolean(FALLBACK, true)
        Log.i("HALO_TTS_PROVIDER_RESOLVED", "requested=$id; effective=${selected.metadata.id}; fallbackAllowed=$allowFallback")
        var fallbackUsed = false
        var hasProducedPcm = false
        val stopped = java.util.concurrent.atomic.AtomicBoolean()
        fun ensureCurrentSpeech() {
            if (stopped.get() || epoch != stopEpoch.get()) throw CancellationException("Speech response stopped")
            if (!fallbackUsed && !selected.metadata.local && !allowCloudSpeech())
                throw CancellationException("Cloud speech permission changed")
        }
        fun androidRequest(request: SpeechRequest) = request.copy(voiceId = null, modelId = null,
            voiceSettings = if (privateOnly || !allowCloudSpeech()) mapOf("_haloOfflineOnly" to 1.0) else emptyMap())
        fun primaryRequest(request: SpeechRequest): SpeechRequest {
            val current = pinned.copy(text = request.text)
            return if (selected === android && !allowCloudSpeech())
                current.copy(voiceSettings = current.voiceSettings + ("_haloOfflineOnly" to 1.0)) else current
        }
        val wrapper = object : SegmentedPcmSpeechProvider, SpeechSynthesisProvider by selected {
            override val metadata get() = if (fallbackUsed) android.metadata else selected.metadata
            override fun stopCurrent() {
                stopped.set(true)
                selected.stopCurrent()
                if (selected !== android) android.stopCurrent()
            }
            override fun pinSpeechRequest(request: SpeechRequest) = pinned.copy(text=request.text)
            override fun streamPcm(request: SpeechRequest) = flow {
                ensureCurrentSpeech()
                val segmented=selected as? SegmentedPcmSpeechProvider
                if(segmented==null || fallbackUsed) {
                    synthesizeToPcm(request)?.let { emit(it) }
                } else {
                    try {
                        segmented.streamPcm(pinned.copy(text=request.text)).collect { pcm ->
                            ensureCurrentSpeech()
                            hasProducedPcm=true; emit(pcm)
                        }
                    } catch(cancelled: CancellationException) { throw cancelled }
                    catch(error: Exception) {
                        ensureCurrentSpeech()
                        if(hasProducedPcm || !allowFallback || selected===android) throw error
                        fallbackUsed=true
                        android.synthesizeToPcm(androidRequest(request))
                            ?.let { ensureCurrentSpeech(); hasProducedPcm=true; emit(it) } ?: throw error
                    }
                }
            }
            override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech? {
                ensureCurrentSpeech()
                val startedAt = SystemClock.elapsedRealtime()
                val pcm = try {
                    if (!fallbackUsed && selected is ElevenLabsTtsProvider && (pinned.voiceId == null || pinned.modelId == null)) error("Speech selection is incomplete")
                    if (fallbackUsed) android.synthesizeToPcm(androidRequest(request)) else selected.synthesizeToPcm(primaryRequest(request)) }
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
                ensureCurrentSpeech()
                if (pcm != null) {
                    hasProducedPcm = true
                    Log.i("HALO_TTS_SYNTHESIS", "provider=${if (fallbackUsed) android.metadata.id else selected.metadata.id}; audioBytes=${pcm.chunks.sumOf { it.size.toLong() }}; sampleRate=${pcm.sampleRateHz}; durationMs=${SystemClock.elapsedRealtime() - startedAt}")
                    return pcm
                }
                if (selected !== android && allowFallback && !hasProducedPcm && !fallbackUsed) {
                    Log.i("HALO_TTS_PROVIDER_FALLBACK", "from=${selected.metadata.id}; to=${android.metadata.id}; reason=synthesis_failed")
                    fallbackUsed = true
                    val fallbackPcm = android.synthesizeToPcm(androidRequest(request))
                    ensureCurrentSpeech()
                    hasProducedPcm = fallbackPcm != null
                    return fallbackPcm
                }
                return null
            }
        }
        return wrapper to pinned
    }
    fun stopCurrent() { stopEpoch.incrementAndGet(); providers.forEach { it.stopCurrent() } }
    suspend fun close() {
        stopCurrent()
        withContext(NonCancellable) {
            var failure: Exception? = null
            providers.forEach { provider ->
                try { provider.close() } catch (error: Exception) {
                    val first = failure
                    if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
                }
            }
            failure?.let { throw it }
        }
    }
    companion object { const val FALLBACK = "tts_cloud_android_fallback" }
}
