package com.halo.providers.elevenlabs.tts

import com.halo.sdk.host.AudioFrame
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ElevenLabsTtsState(val configured: Boolean, val ready: Boolean = false, val error: String? = null)

data class SpeechChoice(val id: String, val name: String)
data class ElevenLabsCatalogue(val voices: List<SpeechChoice>, val models: List<SpeechChoice>, val fetchedAtMs: Long) {
    val stale: Boolean get() = System.currentTimeMillis() - fetchedAtMs > 24 * 60 * 60 * 1000L
}

/** Cloud synthesis only: playback and audio route ownership remain in the host. */
class ElevenLabsTtsProvider(private val storage: Storage) : PcmSpeechSynthesisProvider, CredentialProvider {
    override val metadata = ProviderMetadata(ID, "ElevenLabs", local = false, supportsStreaming = false)
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    private val discoveryMutex = Mutex()
    private val configurationVersion = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var healthy = false
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(ElevenLabsTtsState(hasCredential()))
    val state: kotlinx.coroutines.flow.StateFlow<ElevenLabsTtsState> = _state
    private fun publishState(error: String? = null) {
        _state.value = ElevenLabsTtsState(hasCredential(), hasCredential() && healthy && voiceId() != null && modelId() != null, error)
    }
    override fun hasCredential() = !storage.getString(KEY).isNullOrBlank()
    override fun credential(): Credential? = storage.getString(KEY)?.takeIf { it.isNotBlank() }?.let { Credential(ID, it) }
    override fun setCredential(credential: Credential) { configurationVersion.incrementAndGet(); stopCurrent(); storage.putString(KEY, credential.value.trim()); healthy = false; publishState() }
    override fun clearCredential() { configurationVersion.incrementAndGet(); stopCurrent(); storage.remove(KEY); healthy = false; publishState() }
    fun voiceId(): String? = storage.getString(VOICE)?.takeIf { it.isNotBlank() }
    fun modelId(): String? = storage.getString(MODEL)?.takeIf { it.isNotBlank() }
    fun selectVoice(id: String) { require(cachedCatalogue()?.voices?.any { it.id == id } == true); configurationVersion.incrementAndGet(); storage.putString(VOICE, id); healthy = false; publishState() }
    fun selectModel(id: String) { require(cachedCatalogue()?.models?.any { it.id == id } == true); configurationVersion.incrementAndGet(); storage.putString(MODEL, id); healthy = false; publishState() }
    fun pin(request: SpeechRequest) = request.copy(voiceId = voiceId(), modelId = modelId())

    override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech? {
        val version = configurationVersion.get()
        require(request.text.isNotBlank() && request.text.length <= 4_000) { "Speech text exceeds the supported limit" }
        val voice = request.voiceId ?: voiceId() ?: error("Select an ElevenLabs voice")
        val model = request.modelId ?: modelId() ?: error("Select an ElevenLabs TTS model")
        val known = cachedCatalogue()?.models?.any { it.id == model } == true
        check(known) { "Refresh and select a supported TTS model" }
        val url = BASE.toHttpUrl().newBuilder().addPathSegments("v1/text-to-speech")
            .addPathSegment(voice).addPathSegment("stream").addQueryParameter("output_format", "pcm_24000").build()
        val body = JSONObject().put("text", request.text).put("model_id", model)
        val bytes = fetch(Request.Builder().url(url).post(body.toString().toRequestBody(JSON)), MAX_PCM)
        check(bytes.isNotEmpty() && bytes.size % 2 == 0) { "Invalid PCM response" }
        if (configurationVersion.get() == version && voiceId() == voice && modelId() == model) { healthy = true; publishState() }
        return PcmSpeech(24_000, 1, PcmEncoding.SIGNED_16_LE, bytes.asListOfChunks())
    }

    // No audio is played by a synthesis provider; successful means PCM was produced.
    override suspend fun synthesize(request: SpeechRequest): SpeechResult {
        val pcm = synthesizeToPcm(request) ?: return SpeechResult(0, false)
        return SpeechResult(pcm.chunks.sumOf { it.size.toLong() } * 1000 / 48_000, true)
    }
    override fun stream(text: String): Flow<AudioFrame> = emptyFlow()
    override suspend fun health() = healthy && hasCredential() && voiceId() != null && modelId() != null
    override suspend fun healthDetails() = ProviderHealth(health(), _state.value.error ?: if (!hasCredential()) "Not configured" else if (health()) "Ready" else "Configured; not checked")
    override fun stopCurrent() { calls.forEach { it.cancel() } }
    override suspend fun close() { stopCurrent(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }

    fun cachedCatalogue(): ElevenLabsCatalogue? = runCatching {
        val json = JSONObject(storage.getString(CACHE) ?: return null)
        fun choices(key: String): List<SpeechChoice> = json.getJSONArray(key).let { array ->
            (0 until array.length()).map { index -> array.getJSONObject(index).let { SpeechChoice(it.getString("id"), it.getString("name")) } }
        }
        ElevenLabsCatalogue(choices("voices"), choices("models"), json.getLong("at"))
    }.getOrNull()

    /** Explicit user action only. Failure preserves the last successful non-secret catalogue. */
    suspend fun refreshCatalogue(): ElevenLabsCatalogue = discoveryMutex.withLock {
        val version = configurationVersion.get()
        val modelsJson = JSONArray(String(fetch(Request.Builder().url(BASE + "v1/models"), MAX_METADATA), Charsets.UTF_8))
        val models = (0 until modelsJson.length()).map { modelsJson.getJSONObject(it) }
            .filter { it.optBoolean("can_do_text_to_speech", false) }
            .map { SpeechChoice(it.getString("model_id"), it.optString("name", it.getString("model_id"))) }
        val voices = mutableListOf<SpeechChoice>()
        var token: String? = null
        var more = true
        repeat(10) {
            if (more) {
                val url = BASE.toHttpUrl().newBuilder().addPathSegments("v2/voices").addQueryParameter("page_size", "100")
                    .addQueryParameter("include_total_count", "false").apply { token?.let { addQueryParameter("next_page_token", it) } }.build()
                val page = JSONObject(String(fetch(Request.Builder().url(url), MAX_METADATA), Charsets.UTF_8))
                val array = page.getJSONArray("voices")
                for (index in 0 until array.length()) array.getJSONObject(index).let {
                    voices += SpeechChoice(it.getString("voice_id"), it.optString("name", it.getString("voice_id")))
                }
                more = page.optBoolean("has_more", false)
                token = page.optString("next_page_token").takeIf { it.isNotBlank() && it != "null" }
                check(!more || token != null) { "Voice pagination was incomplete" }
            }
        }
        check(!more) { "Voice catalogue exceeds the bounded discovery limit" }
        val result = ElevenLabsCatalogue(voices.distinctBy { it.id }, models, System.currentTimeMillis())
        fun json(list: List<SpeechChoice>) = JSONArray().apply { list.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } }
        check(configurationVersion.get() == version) { "Speech configuration changed during discovery" }
        storage.putString(CACHE, JSONObject().put("voices", json(result.voices)).put("models", json(models)).put("at", result.fetchedAtMs).toString())
        publishState()
        result
    }

    private suspend fun fetch(builder: Request.Builder, limit: Int): ByteArray {
        val version = configurationVersion.get()
        val key = credential()?.value ?: error("ElevenLabs is not configured")
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(builder.header("xi-api-key", key).build())
            calls.add(call)
            continuation.invokeOnCancellation { call.cancel(); calls.remove(call) }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    calls.remove(call)
                    if (continuation.isActive && configurationVersion.get() == version) { healthy = false; publishState("Speech service is unavailable") }
                    if (continuation.isActive) continuation.resumeWithException(IOException("Speech service is unavailable"))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            check(it.isSuccessful) { when (it.code) { 401, 403 -> "Speech authentication failed"; 429 -> "Speech service rate limited"; else -> "Speech service error (${it.code})" } }
                            val body = it.body ?: error("Empty speech response")
                            check(body.contentLength() <= limit) { "Speech response exceeds size limit" }
                            body.byteStream().use { input ->
                                val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                                while (true) {
                                    val count = input.read(buffer); if (count < 0) break
                                    check(output.size().toLong() + count <= limit) { "Speech response exceeds size limit" }
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            }
                        }
                    }
                    calls.remove(call)
                    if (continuation.isActive) result.fold({ continuation.resume(it) }, { if (configurationVersion.get() == version) { healthy = false; publishState("Speech service request failed") }; continuation.resumeWithException(it) })
                }
            })
        }
    }
    private fun ByteArray.asListOfChunks(): List<ByteArray> = (indices step 8192).map { copyOfRange(it, minOf(size, it + 8192)) }
    companion object {
        const val ID = "provider.elevenlabs.tts"
        const val KEY = "elevenlabs_api_key"
        const val VOICE = "elevenlabs_voice_id"
        const val MODEL = "elevenlabs_tts_model_id"
        private const val CACHE = "elevenlabs_tts_catalogue"
        private const val BASE = "https://api.elevenlabs.io/"
        private const val MAX_PCM = 16 * 1024 * 1024
        private const val MAX_METADATA = 2 * 1024 * 1024
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
