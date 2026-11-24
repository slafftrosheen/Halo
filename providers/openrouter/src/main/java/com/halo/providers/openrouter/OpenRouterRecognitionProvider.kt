package com.halo.providers.openrouter

import com.halo.sdk.host.AudioBuffer
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OpenRouterRecognitionModel(val id: String, val name: String)
data class OpenRouterRecognitionState(val models: List<OpenRouterRecognitionModel> = emptyList(),
    val modelId: String? = null, val configured: Boolean = false,
    val sourceVersion: String = java.util.UUID.randomUUID().toString())

/** Dedicated transcription, not conversational audio. No transcript/audio/credential logging.
 * https://openrouter.ai/docs/guides/overview/multimodal/stt */
class OpenRouterRecognitionProvider(private val storage: Storage, private val language: () -> String,
    private val policy: RemoteDispatchPolicy,
    private val client: OkHttpClient = OpenRouterSpeechProvider.defaultClient(),
    private val baseUrl: String = "https://openrouter.ai/api/v1/",
) : BufferedSpeechRecognitionProvider {
    override val metadata = ProviderMetadata(ID, "OpenRouter transcription", local = false, supportsStreaming = false)
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    private val epoch = AtomicLong()
    private val mutable = MutableStateFlow(OpenRouterRecognitionState())
    val state = mutable.asStateFlow()
    init { reloadConfigurationNow() }
    fun reloadConfigurationNow() {
        stopCurrent()
        mutable.value = state.value.copy(modelId = storage.getString(MODEL),
            configured = !storage.getString(OpenRouterSpeechProvider.KEY).isNullOrBlank(),
            sourceVersion = java.util.UUID.randomUUID().toString())
    }
    fun validateSelection(modelId: String) {
        require(state.value.models.any { it.id == modelId }) { "Load transcription models and choose one from the list" }
    }
    suspend fun refreshModels(): List<OpenRouterRecognitionModel> {
        val version = epoch.get()
        val raw = fetch(Request.Builder().url(baseUrl.toHttpUrl().newBuilder().addPathSegment("models")
            .addQueryParameter("output_modalities", "transcription").build()), version, 4 * 1024 * 1024)
        val array = JSONObject(raw.decodeToString()).getJSONArray("data")
        require(array.length() <= 10000)
        val models = (0 until array.length()).map { array.getJSONObject(it) }.filter { entry ->
            entry.optJSONObject("architecture")?.optJSONArray("output_modalities")?.let { outputs ->
                (0 until outputs.length()).any { outputs.optString(it) == "transcription" }
            } == true
        }.mapNotNull { entry ->
            val id = entry.getString("id")
            if (id.length !in 1..200 || id.any(Char::isISOControl)) null else OpenRouterRecognitionModel(id,
                entry.optString("name", id).filterNot(Char::isISOControl).take(200).ifBlank { id })
        }.distinctBy { it.id }
        ensureCurrent(version)
        mutable.value = state.value.copy(models = models, configured = true, sourceVersion = java.util.UUID.randomUUID().toString())
        return models
    }
    override fun captureContext(): RecognitionCaptureContext {
        val revision = policy.revision
        return RecognitionCaptureContext(ID, revision, RemoteDispatchContext.intent.asContextElement(
            RemoteDispatchIntent(RemoteDispatchRequest(ID, RemoteOperation.HOSTED_SPEECH, setOf(RemoteDataCategory.AUDIO)), revision)))
    }
    override suspend fun recognize(audio: AudioBuffer): RecognitionResult = recognizeInLanguage(audio, language())

    override suspend fun recognizeInLanguage(audio: AudioBuffer, language: String): RecognitionResult {
        // Missing capture authority (e.g. an old memo or Local capture) never uploads.
        val intent = RemoteDispatchContext.intent.get()
        if (intent?.request?.providerId != ID || intent.revision != policy.revision ||
            RemoteDataCategory.AUDIO !in intent.request.categories) throw RemoteDispatchBlocked()
        val version = epoch.get()
        val model = storage.getString(MODEL)?.takeIf { it.isNotBlank() }
            ?: error("Choose an OpenRouter transcription model in Voice settings")
        val locale = language.substringBefore('-').substringBefore('_').lowercase(java.util.Locale.ROOT)
        val wav = wav(audio)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .apply { if (locale.matches(Regex("[a-z]{2}"))) addFormDataPart("language", locale) }.build()
        ensureCurrent(version)
        val raw = fetch(Request.Builder().url(baseUrl.toHttpUrl().newBuilder().addPathSegments("audio/transcriptions").build())
            .post(body), version, 1024 * 1024)
        val text = try { JSONObject(raw.decodeToString()).get("text") }
            catch (_: org.json.JSONException) { throw IllegalArgumentException("Invalid transcription response") }
        require(text is String && text.length <= 120000) { "Invalid transcription response" }
        ensureCurrent(version)
        if (intent.revision != policy.revision) throw RemoteDispatchBlocked()
        return RecognitionResult(text.trim(), if (locale == "auto") "und" else locale)
    }
    override suspend fun health() = state.value.configured && !state.value.modelId.isNullOrBlank()
    override suspend fun healthDetails() = ProviderHealth(health(), message = if (health()) null else "Choose an OpenRouter transcription model in Power mode")
    fun stopCurrent() { epoch.incrementAndGet(); calls.forEach { it.cancel() } }
    override suspend fun close() { stopCurrent() }
    private fun ensureCurrent(version: Long) { if (epoch.get() != version) throw CancellationException("Transcription settings changed") }
    private suspend fun fetch(builder: Request.Builder, version: Long, limit: Int): ByteArray {
        ensureCurrent(version)
        val key = storage.getString(OpenRouterSpeechProvider.KEY)?.takeIf { it.isNotBlank() }
            ?: error("Add your OpenRouter key in AI services")
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(builder.header("Authorization", "Bearer $key").build())
            calls.add(call)
            continuation.invokeOnCancellation { call.cancel(); calls.remove(call) }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    calls.remove(call)
                    if (continuation.isActive) continuation.resumeWithException(if (call.isCanceled() || epoch.get() != version)
                        CancellationException("Transcription cancelled") else IOException("OpenRouter transcription could not connect"))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use {
                        check(it.isSuccessful) { "OpenRouter transcription request failed (HTTP ${it.code})" }
                        check(it.header("Content-Type")?.substringBefore(';')?.trim() == "application/json") { "Invalid transcription response" }
                        val body = checkNotNull(it.body)
                        check(body.contentLength() <= limit) { "Transcription response is too large" }
                        body.byteStream().use { input ->
                            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            while (true) {
                                ensureCurrent(version)
                                val count = input.read(buffer); if (count < 0) break
                                check(output.size().toLong() + count <= limit) { "Transcription response is too large" }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                    } }
                    calls.remove(call)
                    if (continuation.isActive) {
                        if (call.isCanceled() || epoch.get() != version || storage.getString(OpenRouterSpeechProvider.KEY) != key)
                            continuation.resumeWithException(CancellationException("Transcription settings changed"))
                        else result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
                    }
                }
            })
        }
    }
    companion object {
        const val ID = "provider.openrouter.stt"
        const val MODEL = "openrouter_stt_model"
        internal fun wav(audio: AudioBuffer): ByteArray {
            val first = audio.frames.firstOrNull() ?: error("No audio captured")
            require(first.sampleRate in 8000..48000 && first.channels in 1..2) { "Unsupported audio format" }
            var samples = 0L
            audio.frames.forEach {
                require(it.sampleRate == first.sampleRate && it.channels == first.channels && it.samples.size % first.channels == 0) { "Audio format changed during capture" }
                samples += it.samples.size
                require(samples <= 120L * first.sampleRate * first.channels) { "Split recordings longer than two minutes" }
            }
            require(samples > 0)
            val size = (samples * 2).toInt()
            val out = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN)
            out.put("RIFF".toByteArray()).putInt(36 + size).put("WAVEfmt ".toByteArray()).putInt(16)
                .putShort(1).putShort(first.channels.toShort()).putInt(first.sampleRate)
                .putInt(first.sampleRate * first.channels * 2).putShort((first.channels * 2).toShort()).putShort(16)
                .put("data".toByteArray()).putInt(size)
            audio.frames.forEach { frame -> frame.samples.forEach(out::putShort) }
            return out.array()
        }
    }
}
