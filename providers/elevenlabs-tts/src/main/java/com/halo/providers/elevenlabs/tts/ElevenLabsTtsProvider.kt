package com.halo.providers.elevenlabs.tts

import com.halo.sdk.host.AudioFrame
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

data class ElevenLabsTtsState(
    val configured: Boolean = false,
    val ready: Boolean = false,
    val error: String? = null,
    val authenticated: Boolean = false,
    val errorCode: SpeechErrorCode? = null,
    val voiceId: String? = null,
    val modelId: String? = null,
    val voiceName: String? = null,
    val modelName: String? = null,
    val catalogue: ElevenLabsCatalogue? = null,
    val style: Map<String, Double> = mapOf("stability" to 0.5, "similarity_boost" to 0.75, "speed" to 1.0),
)

data class SpeechChoice(val id: String, val name: String, val category: String? = null)
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
    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var authenticated = false
    @Volatile private var lastErrorCode: SpeechErrorCode? = null
    @Volatile private var healthy = false
    private fun initialConfigured(): Boolean = runCatching { hasCredential() }.getOrDefault(false)
    private val _state = MutableStateFlow(ElevenLabsTtsState(configured = initialConfigured()))
    val state: StateFlow<ElevenLabsTtsState> = _state

    init {
        reloadConfiguration()
    }

    private fun publishState(error: String? = null) {
        publishStateSync(error)
        providerScope.launch {
            publishStateInternal(error)
        }
    }

    private fun publishStateSync(error: String? = null) {
        val hasKey = hasCredential()
        _state.value = _state.value.copy(
            configured = hasKey,
            ready = hasKey && healthy && voiceId() != null && modelId() != null,
            error = error,
            authenticated = authenticated && hasKey,
            errorCode = lastErrorCode,
            voiceId = voiceId(),
            modelId = modelId(),
        )
    }

    private suspend fun publishStateInternal(error: String? = null) = withContext(Dispatchers.IO) {
        val hasKey = hasCredential()
        val vId = voiceId()
        val mId = modelId()
        val cat = cachedCatalogue()
        val vName = cat?.voices?.firstOrNull { it.id == vId }?.name
        val mName = cat?.models?.firstOrNull { it.id == mId }?.name
        val styleMap = voiceStyle()

        _state.value = ElevenLabsTtsState(
            configured = hasKey,
            ready = hasKey && healthy && vId != null && mId != null,
            error = error,
            authenticated = authenticated && hasKey,
            errorCode = lastErrorCode,
            voiceId = vId,
            modelId = mId,
            voiceName = vName,
            modelName = mName,
            catalogue = cat,
            style = styleMap,
        )
    }

    fun reloadConfiguration() { publishState() }
    override fun hasCredential() = !storage.getString(KEY).isNullOrBlank()
    override fun credential(): Credential? = storage.getString(KEY)?.takeIf { it.isNotBlank() }?.let { Credential(ID, it) }
    override fun setCredential(credential: Credential) { configurationVersion.incrementAndGet(); stopCurrent(); storage.putString(KEY, credential.value.trim()); healthy = false; authenticated = false; lastErrorCode = null; publishState() }
    override fun clearCredential() { configurationVersion.incrementAndGet(); stopCurrent(); storage.remove(KEY); healthy = false; authenticated = false; lastErrorCode = null; publishState() }
    fun voiceId(): String? = storage.getString(VOICE)?.takeIf { it.isNotBlank() }
    fun modelId(): String? = storage.getString(MODEL)?.takeIf { it.isNotBlank() }
    fun selectVoice(id: String) { require(cachedCatalogue()?.voices?.any { it.id == id } == true); configurationVersion.incrementAndGet(); storage.putString(VOICE, id); healthy = false; authenticated = false; lastErrorCode = null; publishState() }
    fun selectModel(id: String) { require(cachedCatalogue()?.models?.any { it.id == id } == true); configurationVersion.incrementAndGet(); storage.putString(MODEL, id); healthy = false; authenticated = false; lastErrorCode = null; publishState() }
    fun voiceStyle(): Map<String, Double> = mapOf(
        "stability" to (storage.getString("elevenlabs_stability")?.toDoubleOrNull() ?: 0.5),
        "similarity_boost" to (storage.getString("elevenlabs_similarity_boost")?.toDoubleOrNull() ?: 0.75),
        "speed" to (storage.getString("elevenlabs_speed")?.toDoubleOrNull() ?: 1.0))
    fun saveVoiceStyle(stability: Double, similarity: Double, speed: Double) {
        require(stability.isFinite() && stability in 0.0..1.0 && similarity.isFinite() && similarity in 0.0..1.0 && speed.isFinite() && speed in 0.7..1.2)
        storage.putBatch(strings = mapOf("elevenlabs_stability" to stability.toString(), "elevenlabs_similarity_boost" to similarity.toString(), "elevenlabs_speed" to speed.toString()))
    }
    fun pin(request: SpeechRequest) = request.copy(voiceId = voiceId(), modelId = modelId(), voiceSettings = voiceStyle())

    override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech? {
        val version = configurationVersion.get()
        return try { synthesizeBounded(request) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: SpeechProviderException) {
            if (configurationVersion.get() == version) { healthy = false; lastErrorCode = error.code; publishState(error.code.name) }
            throw error
        }
    }

    private suspend fun synthesizeBounded(request: SpeechRequest): PcmSpeech {
        val version = configurationVersion.get()
        require(request.text.isNotBlank() && request.text.length <= 24_000) { "Speech text exceeds the supported limit" }
        val voice = request.voiceId ?: voiceId() ?: throw SpeechProviderException(SpeechErrorCode.VOICE_NOT_FOUND)
        val model = request.modelId ?: modelId() ?: throw SpeechProviderException(SpeechErrorCode.MODEL_NOT_AVAILABLE)
        val known = cachedCatalogue()?.models?.any { it.id == model } == true
        if (!known) throw SpeechProviderException(SpeechErrorCode.MODEL_NOT_AVAILABLE)
        val url = BASE.toHttpUrl().newBuilder().addPathSegments("v1/text-to-speech")
            .addPathSegment(voice).addQueryParameter("output_format", "pcm_24000").build()
        val output = ByteArrayOutputStream()
        val style = request.voiceSettings.ifEmpty { voiceStyle() }
        check(style.all { (key, value) -> value.isFinite() && when (key) { "stability", "similarity_boost" -> value in 0.0..1.0; "speed" -> value in 0.7..1.2; else -> false } })
        // All bounded chunks complete before host playback, so fallback never splices voices mid-response.
        var remaining = request.text
        while (remaining.isNotEmpty()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            var end = minOf(4_000, remaining.length)
            if (end < remaining.length) {
                val boundary = remaining.lastIndexOf(' ', end - 1)
                if (boundary >= 2_000) end = boundary + 1
                else if (remaining[end - 1].isHighSurrogate()) end--
            }
            val body = JSONObject().put("text", remaining.substring(0, end)).put("model_id", model).put("voice_settings", JSONObject(style))
            val bytes = fetch(Request.Builder().url(url).post(body.toString().toRequestBody(JSON)), MAX_PCM)
            if (bytes.isEmpty() || bytes.size % 2 != 0 || output.size().toLong() + bytes.size > 32 * 1024 * 1024) throw SpeechProviderException(SpeechErrorCode.INVALID_AUDIO_RESPONSE)
            output.write(bytes); remaining = remaining.substring(end)
        }
        val bytes = output.toByteArray()
        if (configurationVersion.get() == version && voiceId() == voice && modelId() == model) { healthy = true; authenticated = true; lastErrorCode = null; publishState() }
        return PcmSpeech(24_000, 1, PcmEncoding.SIGNED_16_LE, bytes.asListOfChunks())
    }

    // No audio is played by a synthesis provider; successful means PCM was produced.
    override suspend fun synthesize(request: SpeechRequest): SpeechResult {
        val pcm = synthesizeToPcm(request) ?: return SpeechResult(0, false)
        return SpeechResult(pcm.chunks.sumOf { it.size.toLong() } * 1000 / 48_000, true)
    }
    override fun stream(text: String): Flow<AudioFrame> = emptyFlow()
    /** Explicit lightweight authenticated check; no speech generation. Never called on screen entry. */
    override suspend fun health(): Boolean {
        if (!hasCredential()) { lastErrorCode = SpeechErrorCode.NOT_CONFIGURED; publishState("Not configured"); return false }
        val version = configurationVersion.get()
        return try {
            JSONArray(String(fetch(Request.Builder().url(BASE + "v1/models"), MAX_METADATA), Charsets.UTF_8))
            if (configurationVersion.get() != version) false else { authenticated = true; lastErrorCode = null; publishState(); true }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { if (configurationVersion.get() == version) { authenticated = false; lastErrorCode = (error as? SpeechProviderException)?.code ?: SpeechErrorCode.NETWORK_ERROR; publishState(lastErrorCode?.name) }; false }
    }
    override suspend fun healthDetails(): ProviderHealth { val ready = health(); return ProviderHealth(ready, _state.value.error ?: if (ready) "Authenticated; synthesis ${if (healthy) "proven" else "untested"}" else "Not ready") }
    override fun stopCurrent() { calls.forEach { it.cancel() } }
    override suspend fun close() { stopCurrent(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }

    fun cachedCatalogue(): ElevenLabsCatalogue? = runCatching {
        val json = JSONObject(storage.getString(CACHE) ?: return null)
        fun choices(key: String): List<SpeechChoice> = json.getJSONArray(key).let { array ->
            (0 until array.length()).map { index -> array.getJSONObject(index).let { SpeechChoice(it.getString("id"), it.getString("name"), it.optString("category").takeIf { value -> value.isNotBlank() }) } }
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
                    voices += SpeechChoice(it.getString("voice_id"), it.optString("name", it.getString("voice_id")), it.optString("category").takeIf { value -> value.isNotBlank() })
                }
                more = page.optBoolean("has_more", false)
                token = page.optString("next_page_token").takeIf { it.isNotBlank() && it != "null" }
                check(!more || token != null) { "Voice pagination was incomplete" }
            }
        }
        check(!more) { "Voice catalogue exceeds the bounded discovery limit" }
        val result = ElevenLabsCatalogue(voices.distinctBy { it.id }, models, System.currentTimeMillis())
        fun json(list: List<SpeechChoice>) = JSONArray().apply { list.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("category", it.category)) } }
        check(configurationVersion.get() == version) { "Speech configuration changed during discovery" }
        storage.putString(CACHE, JSONObject().put("voices", json(result.voices)).put("models", json(models)).put("at", result.fetchedAtMs).toString())
        publishState()
        result
    }

    private suspend fun fetch(builder: Request.Builder, limit: Int): ByteArray {
        val version = configurationVersion.get()
        val key = credential()?.value ?: throw SpeechProviderException(SpeechErrorCode.NOT_CONFIGURED)
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(builder.header("xi-api-key", key).build())
            calls.add(call)
            continuation.invokeOnCancellation { call.cancel(); calls.remove(call) }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    calls.remove(call)
                    if (continuation.isActive && configurationVersion.get() == version) { healthy = false; lastErrorCode = SpeechErrorCode.NETWORK_ERROR; publishState(lastErrorCode?.name) }
                    if (continuation.isActive) continuation.resumeWithException(SpeechProviderException(SpeechErrorCode.NETWORK_ERROR))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw SpeechProviderException(when (it.code) { 401, 403 -> SpeechErrorCode.AUTHENTICATION_FAILED; 429 -> SpeechErrorCode.RATE_LIMITED; 404 -> SpeechErrorCode.VOICE_NOT_FOUND; 400, 422 -> SpeechErrorCode.MODEL_NOT_AVAILABLE; else -> SpeechErrorCode.SYNTHESIS_FAILED })
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
                    if (continuation.isActive) result.fold({ continuation.resume(it) }, { if (configurationVersion.get() == version) { healthy = false; lastErrorCode = (it as? SpeechProviderException)?.code ?: SpeechErrorCode.SYNTHESIS_FAILED; publishState(lastErrorCode?.name) }; continuation.resumeWithException(it) })
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
