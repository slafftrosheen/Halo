package com.halo.providers.litert

import com.google.ai.edge.litertlm.*
import android.graphics.BitmapFactory
import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EmbeddingUnavailableException(val reasonCode: String, cause: Throwable? = null):
    IllegalStateException(reasonCode, cause)

data class EmbeddingExecutionMetrics(val modelSha256: String, val dimension: Int, val batchSize: Int,
    val backend: String, val initMs: Long, val generationMs: Long, val estimatedMemoryBytes: Long)

/**
 * Native multimodal embedding input. This is an executor contract, not authorization to
 * scan Gallery/Notebook files. Caller must separately resolve exact user-consented sources.
 * Byte payloads are bounded and checked again by the executor before native allocation.
 */
sealed interface LocalEmbeddingInput {
    data class Text(val text: String): LocalEmbeddingInput
    data class Image(val encodedPngOrJpeg: ByteArray): LocalEmbeddingInput
    data class Audio(val encodedWav: ByteArray): LocalEmbeddingInput
}

data class LocalMediaEmbeddingRequest(val modelSha256: String, val dimension: Int,
    val inputs: List<LocalEmbeddingInput>,
    val preprocessingIdentity: String = "embeddinggemma2-native-media-v1") {
    init {
        require(modelSha256.matches(Regex("[a-f0-9]{64}")))
        require(dimension in 1..16_384)
        require(inputs.isNotEmpty() && inputs.size <= 8)
        require(preprocessingIdentity == "embeddinggemma2-native-media-v1")
        inputs.forEach(LocalMediaEmbeddingSafety::check)
    }
}

object LocalMediaEmbeddingSafety {
    const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
    const val MAX_AUDIO_BYTES = 8 * 1024 * 1024
    fun check(input: LocalEmbeddingInput) {
        when (input) {
            is LocalEmbeddingInput.Text ->
                require(input.text.isNotBlank() && input.text.length <= 32768) { "TEXT_INPUT_INVALID" }
            is LocalEmbeddingInput.Image -> {
                val bytes = input.encodedPngOrJpeg
                val png = bytes.size >= 24 && bytes[0] == 0x89.toByte() &&
                    bytes[1] == 0x50.toByte() && bytes[2] == 0x4e.toByte() && bytes[3] == 0x47.toByte()
                val jpeg = bytes.size >= 4 && bytes[0] == 0xff.toByte() &&
                    bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
                require(bytes.size in 24..MAX_IMAGE_BYTES && (png || jpeg)) { "IMAGE_INPUT_INVALID" }
            }
            is LocalEmbeddingInput.Audio -> {
                val bytes = input.encodedWav
                val wav = bytes.size >= 44 &&
                    bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                    bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                    bytes[8] == 0x57.toByte() && bytes[9] == 0x41.toByte() &&
                    bytes[10] == 0x56.toByte() && bytes[11] == 0x45.toByte()
                require(bytes.size in 44..MAX_AUDIO_BYTES && wav) { "AUDIO_INPUT_INVALID" }
            }
        }
    }
}

/**
 * Local CPU-first embedding engine, separate from a generative model. No remote fallback,
 * index mutation, file reading, data source discovery or silent image/audio acquisition.
 * Exactly one heavy LiteRT owner is admitted at a time.
 */
class LiteRtEmbeddingProvider(private val inventory: LocalModelInventoryRepository,
    private val resources: NativeResourceCoordinator, private val modelId: String):
    EmbeddingProvider, NativeResourceOwner {
    override val id = "provider.litert.embedding"
    override val nativeResourceId get() = "$id:$modelId"
    override val nativeResourceKind = NativeResourceKind.HEAVY_LLM
    @Volatile private var estimate = 512L * 1024 * 1024
    override val defaultResourcePolicy
        get() = WorkloadResourcePolicy(RuntimeWorkloadClass.TEXT_CHAT, estimate, true)
    private val lock = Mutex()
    private var pendingTeardown: EmbeddingEngine? = null
    private val mutableMetrics = MutableStateFlow<EmbeddingExecutionMetrics?>(null)
    val metrics = mutableMetrics.asStateFlow()

    override suspend fun releaseIfIdle(): Boolean {
        if (!lock.tryLock()) return false
        return try {
            pendingTeardown?.close()
            pendingTeardown = null
            resources.unregister(this)
            true
        } catch (_: Exception) { false } finally { lock.unlock() }
    }

    override suspend fun embed(request: EmbeddingRequest): EmbeddingBatch =
        execute(request.modelSha256, request.dimension, request.preprocessingIdentity,
            request.texts.map { LocalEmbeddingInput.Text(it) })

    /**
     * Native API supports InputData.Image and InputData.Audio in LiteRT-LM 0.18.
     * This is not user-visible support until cross-modal indexing + phone qualification land.
     * Verify each encoder's CPU capability against this exact physical artifact.
     */
    suspend fun embedMedia(request: LocalMediaEmbeddingRequest): EmbeddingBatch =
        execute(request.modelSha256, request.dimension, request.preprocessingIdentity, request.inputs)

    private suspend fun execute(sha: String, dimension: Int, identity: String,
        inputs: List<LocalEmbeddingInput>): EmbeddingBatch =
        withContext(Dispatchers.Default) { lock.withLock {
            require(inputs.isNotEmpty() && inputs.size <= 32)
            inputs.forEach(LocalMediaEmbeddingSafety::check)
            val usesVision = inputs.any { it is LocalEmbeddingInput.Image }
            val usesAudio = inputs.any { it is LocalEmbeddingInput.Audio }
            if (usesVision) inputs.filterIsInstance<LocalEmbeddingInput.Image>().forEach { image ->
                // inJustDecodeBounds never allocates a full bitmap. Keep decompression
                // bombs out of the JNI image decoder despite small compressed bytes.
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(image.encodedPngOrJpeg,0,image.encodedPngOrJpeg.size,options)
                if (options.outWidth !in 1..4096 || options.outHeight !in 1..4096 ||
                    options.outWidth.toLong() * options.outHeight > 16_000_000L)
                    throw EmbeddingUnavailableException("IMAGE_DIMENSIONS_UNSUPPORTED")
            }
            pendingTeardown?.let { previous ->
                try { previous.close() }
                catch (failure: Exception) { throw EmbeddingUnavailableException("PREVIOUS_TEARDOWN_FAILED", failure) }
                pendingTeardown = null
                resources.unregister(this@LiteRtEmbeddingProvider)
            }
            val artifact = inventory.verifyPhysicalArtifact(modelId)
                ?: throw EmbeddingUnavailableException("ARTIFACT_NOT_INSTALLED")
            if (!artifact.verified || artifact.sha256 != sha)
                throw EmbeddingUnavailableException("ARTIFACT_CHANGED")
            // Media encoders have extra allocations. This conservative budget is admission,
            // not a measured peak-RSS claim.
            estimate = artifact.bytes + (if (usesVision || usesAudio) 512L else 256L) * 1024 * 1024
            resources.budgetReevaluation?.invoke()
            if (resources.budget.value.pressure != NativeMemoryPressure.NORMAL ||
                resources.budget.value.availableBytes < estimate)
                throw EmbeddingUnavailableException("MEMORY_NOT_ADMITTED")
            if (resources.snapshot.value.any {
                    it.kind == NativeResourceKind.HEAVY_LLM &&
                        it.ownerId != nativeResourceId && it.state != NativeResourceState.RELEASED
                }) throw EmbeddingUnavailableException("UNLOAD_RESIDENT_LITERT_FIRST")
            if (!resources.acquire(this@LiteRtEmbeddingProvider)) {
                resources.unregister(this@LiteRtEmbeddingProvider)
                throw EmbeddingUnavailableException("NATIVE_RESOURCE_BUSY")
            }
            var engine: EmbeddingEngine? = null
            var primaryFailure: Throwable? = null
            try {
                ModelInfo.from(artifact.path).use { info ->
                    if (info !is ModelInfo.Embedding)
                        throw EmbeddingUnavailableException("TASK_NOT_EMBEDDING")
                    if (info.dimension() != dimension)
                        throw EmbeddingUnavailableException("DIMENSION_UNSUPPORTED")
                    if (BackendType.CPU !in info.supportedBackends(Modality.TEXT))
                        throw EmbeddingUnavailableException("CPU_UNSUPPORTED")
                    val modalities = info.inputModalities()
                    if (usesVision && (!modalities.vision ||
                            BackendType.CPU !in info.supportedBackends(Modality.VISION)))
                        throw EmbeddingUnavailableException("VISION_CPU_UNSUPPORTED")
                    if (usesAudio && (!modalities.audio ||
                            BackendType.CPU !in info.supportedBackends(Modality.AUDIO)))
                        throw EmbeddingUnavailableException("AUDIO_CPU_UNSUPPORTED")
                }
                resources.mark(this@LiteRtEmbeddingProvider, NativeResourceState.LOADING, "Embedding initialization")
                val started = System.nanoTime()
                engine = EmbeddingEngine(EmbeddingEngineConfig(
                    modelPath = artifact.path, backend = Backend.CPU(),
                    visionBackend = if (usesVision) Backend.CPU() else null,
                    audioBackend = if (usesAudio) Backend.CPU() else null))
                engine.initialize()
                val loaded = System.nanoTime()
                resources.mark(this@LiteRtEmbeddingProvider, NativeResourceState.IN_USE, "Embedding computation")
                val vectors = inputs.map { item ->
                    currentCoroutineContext().ensureActive()
                    val native = when (item) {
                        is LocalEmbeddingInput.Text -> InputData.Text(item.text)
                        is LocalEmbeddingInput.Image -> InputData.Image(item.encodedPngOrJpeg.copyOf())
                        is LocalEmbeddingInput.Audio -> InputData.Audio(item.encodedWav.copyOf())
                    }
                    requireNotNull(engine).computeEmbedding(listOf(native),
                        EmbeddingOptions(normalize = true, insertSpecialTokens = true)).embedding.toList()
                }
                currentCoroutineContext().ensureActive()
                val result = EmbeddingBatch(EmbeddingSpace(sha, dimension, true,
                    "litertlm/0.18.0", identity), vectors, System.currentTimeMillis())
                mutableMetrics.value = EmbeddingExecutionMetrics(sha, dimension, inputs.size, "CPU",
                    (loaded-started)/1_000_000, (System.nanoTime()-loaded)/1_000_000, estimate)
                result
            } catch (cancelled: CancellationException) {
                primaryFailure = cancelled; throw cancelled
            } catch (unsupported: EmbeddingUnavailableException) {
                primaryFailure = unsupported; throw unsupported
            } catch (error: Exception) {
                val failure = EmbeddingUnavailableException("NATIVE_${error.javaClass.simpleName.take(64)}", error)
                primaryFailure = failure
                throw failure
            } finally {
                withContext(NonCancellable) {
                    val cleanupFailure = try { engine?.close(); null } catch (failure: Exception) { failure }
                    if (cleanupFailure == null) {
                        resources.unregister(this@LiteRtEmbeddingProvider)
                    } else {
                        pendingTeardown = engine
                        resources.mark(this@LiteRtEmbeddingProvider, NativeResourceState.RESIDENT,
                            "Embedding teardown pending")
                        val primary = primaryFailure
                        if (primary != null) primary.addSuppressed(cleanupFailure)
                        else throw EmbeddingUnavailableException("NATIVE_TEARDOWN_FAILED", cleanupFailure)
                    }
                }
            }
        } }
}
