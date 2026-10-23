package com.halo.providers.litert

import com.google.ai.edge.litertlm.*
import java.io.Closeable

/** Provider-local native boundary. No SDK handles or types escape to the app/runtime. */
data class LiteRtNativeArtifactInfo(val task: String, val textBackends: List<String>,
    val vision: Boolean, val audio: Boolean, val nativeTools: Boolean,
    val embedding: Boolean, val embeddingDimension: Int?, val visionCpu: Boolean, val audioCpu: Boolean)

interface LiteRtNativeArtifactSession : Closeable {
    fun inspect(): LiteRtNativeArtifactInfo
    fun initializeEmbedding(vision: Boolean, audio: Boolean)
    fun computeEmbedding(input: LocalEmbeddingInput): List<Float>
}

fun interface LiteRtNativeArtifactFactory {
    fun open(path: String): LiteRtNativeArtifactSession
}

object SdkLiteRtNativeArtifactFactory : LiteRtNativeArtifactFactory {
    override fun open(path: String): LiteRtNativeArtifactSession = Session(path, ModelInfo.from(path))

    private class Session(private val path: String, private var metadata: ModelInfo?) : LiteRtNativeArtifactSession {
        private var cached: LiteRtNativeArtifactInfo? = null
        private var engine: EmbeddingEngine? = null

        override fun inspect(): LiteRtNativeArtifactInfo = cached ?: run {
            val info = checkNotNull(metadata)
            val modalities = info.inputModalities()
            val embedding = info as? ModelInfo.Embedding
            LiteRtNativeArtifactInfo(info.modelType.name, info.supportedBackends(Modality.TEXT).map { it.name },
                modalities.vision, modalities.audio, (info as? ModelInfo.Llm)?.supportsFunctionCalling() == true,
                embedding != null, embedding?.dimension(),
                embedding != null && modalities.vision && BackendType.CPU in info.supportedBackends(Modality.VISION),
                embedding != null && modalities.audio && BackendType.CPU in info.supportedBackends(Modality.AUDIO))
                .also { cached = it }
        }

        override fun initializeEmbedding(vision: Boolean, audio: Boolean) {
            check(inspect().embedding && engine == null)
            // Release metadata before allocating the engine; the inventory pin covers both.
            metadata?.close()
            metadata = null
            engine = EmbeddingEngine(EmbeddingEngineConfig(modelPath = path, backend = Backend.CPU(),
                visionBackend = if (vision) Backend.CPU() else null,
                audioBackend = if (audio) Backend.CPU() else null))
            checkNotNull(engine).initialize()
        }

        override fun computeEmbedding(input: LocalEmbeddingInput): List<Float> {
            val native = when (input) {
                is LocalEmbeddingInput.Text -> InputData.Text(input.text)
                is LocalEmbeddingInput.Image -> InputData.Image(input.encodedPngOrJpeg.copyOf())
                is LocalEmbeddingInput.Audio -> InputData.Audio(input.encodedWav.copyOf())
            }
            return checkNotNull(engine).computeEmbedding(listOf(native),
                EmbeddingOptions(normalize = true, insertSpecialTokens = true)).embedding.toList()
        }

        override fun close() {
            // Keep each handle reachable if close fails, so the same owner can retry.
            engine?.close()
            engine = null
            metadata?.close()
            metadata = null
        }
    }
}
