package com.halo.providers.openrouter

import com.halo.sdk.host.AudioFrame
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OpenRouterSpeechModel(val id: String, val name: String, val voices: List<String> = emptyList())
data class OpenRouterSpeechState(val models: List<OpenRouterSpeechModel> = emptyList(),
    val modelId: String? = null, val voiceId: String? = null, val configured: Boolean = false,
    val sourceVersion: String = java.util.UUID.randomUUID().toString())

/** Uses the existing OpenRouter credential. The host owns all playback/routing.
 * Only qualified Gemini models use raw PCM (Google documents 24 kHz mono signed LE16).
 * All other models request self-describing MP3; never guess the format of arbitrary PCM.
 * Contract: https://openrouter.ai/docs/guides/overview/multimodal/tts */
class OpenRouterSpeechProvider(
    private val storage: Storage,
    private val decodeMp3: suspend (ByteArray) -> PcmSpeech,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = "https://openrouter.ai/api/v1/",
) : SegmentedPcmSpeechProvider {
    override val metadata = ProviderMetadata(ID, "OpenRouter speech", local = false, supportsStreaming = true)
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    private val epoch = java.util.concurrent.atomic.AtomicLong()
    private val mutableState = MutableStateFlow(OpenRouterSpeechState())
    val state = mutableState.asStateFlow()
    init { reloadConfigurationNow() }

    fun reloadConfigurationNow() {
        stopCurrent()
        mutableState.value = mutableState.value.copy(sourceVersion = java.util.UUID.randomUUID().toString(), modelId = storage.getString(MODEL),
            voiceId = storage.getString(VOICE), configured = !storage.getString(KEY).isNullOrBlank())
    }
    fun configure(modelId: String, voiceId: String) {
        validateSelection(modelId, voiceId)
        storage.putBatch(strings = mapOf(MODEL to modelId, VOICE to voiceId.trim()))
        check(storage.getString(MODEL) == modelId && storage.getString(VOICE) == voiceId.trim()) { "Voice settings could not be saved" }
        reloadConfigurationNow()
    }
    fun validateSelection(modelId: String, voiceId: String) {
        val model = requireNotNull(state.value.models.firstOrNull { it.id == modelId }) { "Refresh speech models and select one from the list" }
        require(voiceId.isNotBlank() && voiceId.length <= 200 && voiceId.none(Char::isISOControl)) { "Enter a voice supported by this speech model" }
        require(model.voices.isEmpty() || voiceId.trim() in model.voices) { "Choose a voice supported by this speech model" }
    }
    suspend fun refreshModels(): List<OpenRouterSpeechModel> {
        val version = epoch.get()
        val json = JSONObject(fetch(Request.Builder().url(baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("models").addQueryParameter("output_modalities", "speech").build()), version, null).decodeToString())
        val entries = json.getJSONArray("data")
        require(entries.length() <= 10000)
        val models = (0 until entries.length()).map { entries.getJSONObject(it) }.filter { entry ->
            val outputs = entry.optJSONObject("architecture")?.optJSONArray("output_modalities")
            outputs != null && (0 until outputs.length()).any { outputs.optString(it) == "speech" }
        }.mapNotNull { entry ->
            val id = entry.getString("id")
            if (id.length !in 1..200 || id.any(Char::isISOControl)) null
            else {
                val voices = entry.optJSONArray("supported_voices")
                require(voices == null || voices.length() <= 200) { "Speech voice catalog is too large" }
                val supported = (0 until (voices?.length() ?: 0)).map { index ->
                    requireNotNull(voices?.opt(index) as? String).also {
                        require(it.length in 1..200 && it.isNotBlank() && it.none(Char::isISOControl))
                    }
                }.distinct()
                OpenRouterSpeechModel(id, entry.optString("name", id).filterNot(Char::isISOControl).take(200).ifBlank { id }, supported)
            }
        }.distinctBy { it.id }
        ensureCurrent(version)
        mutableState.value = state.value.copy(models = models, configured = true, sourceVersion = java.util.UUID.randomUUID().toString())
        return models
    }
    override fun pinSpeechRequest(request: SpeechRequest): SpeechRequest {
        val model = request.modelId ?: storage.getString(MODEL)
        val voice = request.voiceId ?: storage.getString(VOICE)
        if (model.isNullOrBlank() || voice.isNullOrBlank()) throw SpeechProviderException(SpeechErrorCode.NOT_CONFIGURED,
            "Choose an OpenRouter speech model and voice in Voice & reading")
        return request.copy(modelId = model, voiceId = voice)
    }
    override fun streamPcm(request: SpeechRequest): Flow<PcmSpeech> = flow {
        val pinned = pinSpeechRequest(request)
        val version = epoch.get()
        var text = pinned.text
        require(text.length <= 120000) { "Speech text is too long" }
        while (text.isNotBlank()) {
            ensureCurrent(version)
            var end = minOf(3000, text.length)
            if (end < text.length) {
                val boundary = text.lastIndexOf(' ', end - 1)
                if (boundary >= 1500) end = boundary + 1
                else if (text[end - 1].isHighSurrogate()) end--
            }
            emit(synthesizeChunk(pinned.copy(text = text.substring(0, end)), version))
            text = text.substring(end)
        }
    }
    override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech {
        val pinned = pinSpeechRequest(request)
        require(pinned.text.isNotBlank() && pinned.text.length <= 3000) { "Use streamed phrases for long speech" }
        return synthesizeChunk(pinned, epoch.get())
    }
    private suspend fun synthesizeChunk(request: SpeechRequest, version: Long): PcmSpeech {
        val rawPcm = request.modelId in GEMINI_PCM_MODELS
        val body = JSONObject().put("model", request.modelId).put("input", request.text)
            .put("voice", request.voiceId).put("response_format", if (rawPcm) "pcm" else "mp3")
        val bytes = fetch(Request.Builder().url(baseUrl.toHttpUrl().newBuilder().addPathSegments("audio/speech").build())
            .post(body.toString().toRequestBody("application/json".toMediaType())), version, if (rawPcm) "audio/pcm" else "audio/mpeg")
        ensureCurrent(version)
        val pcm = if (rawPcm) {
            // OpenRouter's pcm response is headerless; reject containers and partial samples.
            if (bytes.size % 2 != 0 || bytes.take(4).toByteArray().decodeToString() in setOf("RIFF", "OggS"))
                throw SpeechProviderException(SpeechErrorCode.INVALID_AUDIO_RESPONSE)
            PcmSpeech(24000, 1, PcmEncoding.SIGNED_16_LE, listOf(bytes))
        } else decodeMp3(bytes)
        ensureCurrent(version)
        return pcm
    }
    private fun ensureCurrent(version: Long) { if (epoch.get() != version) throw CancellationException("Speech stopped or configuration changed") }
    private suspend fun fetch(builder: Request.Builder, version: Long, audioType: String?): ByteArray {
        ensureCurrent(version)
        val key = storage.getString(KEY)?.takeIf { it.isNotBlank() } ?: throw SpeechProviderException(SpeechErrorCode.NOT_CONFIGURED, "Add your OpenRouter key in AI services")
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(builder.header("Authorization", "Bearer $key").build())
            calls.add(call)
            continuation.invokeOnCancellation { call.cancel(); calls.remove(call) }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    calls.remove(call)
                    if (continuation.isActive) continuation.resumeWithException(if (call.isCanceled() || epoch.get() != version)
                        CancellationException("Speech cancelled") else SpeechProviderException(SpeechErrorCode.NETWORK_ERROR, "OpenRouter speech could not connect"))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use {
                        if (!it.isSuccessful) throw SpeechProviderException(when (it.code) {
                            401, 403 -> SpeechErrorCode.AUTHENTICATION_FAILED
                            429 -> SpeechErrorCode.RATE_LIMITED
                            else -> SpeechErrorCode.SYNTHESIS_FAILED
                        }, if (it.code == 402) "OpenRouter has insufficient credits for this speech model" else "OpenRouter speech request failed (HTTP ${it.code})")
                        if (audioType != null && it.header("Content-Type")?.substringBefore(';')?.trim() != audioType)
                            throw SpeechProviderException(SpeechErrorCode.INVALID_AUDIO_RESPONSE)
                        val body = it.body ?: throw SpeechProviderException(SpeechErrorCode.INVALID_AUDIO_RESPONSE)
                        val limit = if (audioType != null) 12 * 1024 * 1024 else 4 * 1024 * 1024
                        if (body.contentLength() > limit) throw SpeechProviderException(SpeechErrorCode.INVALID_AUDIO_RESPONSE)
                        body.byteStream().use { input ->
                            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            while (true) {
                                ensureCurrent(version)
                                val count = input.read(buffer); if (count < 0) break
                                if (output.size().toLong() + count > limit) throw SpeechProviderException(SpeechErrorCode.INVALID_AUDIO_RESPONSE)
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray().also { if (it.isEmpty()) throw SpeechProviderException(SpeechErrorCode.INVALID_AUDIO_RESPONSE) }
                        }
                    } }
                    calls.remove(call)
                    if (continuation.isActive) {
                        if (call.isCanceled() || epoch.get() != version || storage.getString(KEY) != key)
                            continuation.resumeWithException(CancellationException("Speech configuration changed"))
                        else result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
                    }
                }
            })
        }
    }
    override suspend fun synthesize(request: SpeechRequest): SpeechResult {
        val pcm = synthesizeToPcm(request)
        return SpeechResult(pcm.chunks.sumOf { it.size.toLong() } * 1000 / (pcm.sampleRateHz * pcm.channelCount * pcm.encoding.bytesPerSample), true)
    }
    override fun stream(text: String): Flow<AudioFrame> = emptyFlow()
    override suspend fun health(): Boolean = try { refreshModels(); true } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
    override fun stopCurrent() { epoch.incrementAndGet(); calls.forEach { it.cancel() } }
    override suspend fun close() { stopCurrent(); client.connectionPool.evictAll() }
    companion object {
        const val ID = "provider.openrouter.tts"
        const val KEY = "openrouter_api_key"
        const val MODEL = "openrouter_tts_model"
        const val VOICE = "openrouter_tts_voice"
        // https://ai.google.dev/gemini-api/docs/speech-generation#output-formats
        // Qualified against OpenRouter's dedicated speech endpoint, not chat audio.
        private val GEMINI_PCM_MODELS = setOf("google/gemini-3.8-flash-lite-tts",
            "google/gemini-3.8-flash-tts", "google/gemini-3.1-flash-tts-preview")
        fun defaultClient() = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    }
}
