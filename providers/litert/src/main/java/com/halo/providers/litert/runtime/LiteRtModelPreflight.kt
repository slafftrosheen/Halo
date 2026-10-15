package com.halo.providers.litert.runtime

import com.google.ai.edge.litertlm.*
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.sdk.provider.LocalComputeBackend

/** The 0.18 metadata API checks the actual file before heavyweight engine initialization. */
internal object LiteRtModelPreflight {
    private data class ThinkingMetadata(val bytes: Long, val modifiedAt: Long, val supported: Boolean)
    private val thinkingMetadata = java.util.concurrent.ConcurrentHashMap<String, ThinkingMetadata>()
    internal fun rememberThinkingSupport(path: String, bytes: Long, modifiedAt: Long, supported: Boolean) {
        if (thinkingMetadata.size >= 256) thinkingMetadata.clear()
        thinkingMetadata[path] = ThinkingMetadata(bytes, modifiedAt, supported)
    }
    internal fun thinkingSupport(path: String, bytes: Long, modifiedAt: Long): Boolean? =
        thinkingMetadata[path]?.takeIf { it.bytes == bytes && it.modifiedAt == modifiedAt }?.supported

    fun check(path: String, backend: LocalComputeBackend, context: Int?, vision: Boolean, audio: Boolean) {
        ModelInfo.from(path).use { info ->
            require(info is ModelInfo.Llm) { "This artifact is not a language generation model" }
            val modalities = info.inputModalities()
            require(modalities.text) { "This artifact has no text input" }
            require(!vision || modalities.vision) { "This artifact has no image encoder" }
            require(!audio || modalities.audio) { "This artifact has no audio encoder" }
            info.minRuntimeVersion()?.let { required ->
                require(versionSupported(required, LiteRtLocalProvider.RUNTIME_VERSION)) {
                    "This model requires LiteRT-LM $required or newer"
                }
            }
            val maximum = info.maxContextTokens()
            require(context == null || maximum <= 0 || context <= maximum) {
                "Requested context $context exceeds the artifact limit $maximum"
            }
            val textBackend = if (backend == LocalComputeBackend.CPU) BackendType.CPU else BackendType.GPU
            requireBackend(info, Modality.TEXT, textBackend)
            if (vision) requireBackend(info, Modality.VISION, BackendType.GPU)
            if (audio) requireBackend(info, Modality.AUDIO, BackendType.CPU)
            val file = java.io.File(path)
            rememberThinkingSupport(path, file.length(), file.lastModified(), info.supportsThinking())
        }
    }
    private fun requireBackend(info: ModelInfo, modality: Modality, backend: BackendType) {
        val supported = info.supportedBackends(modality)
        // Older artifacts may omit backend metadata. Their explicit capability and runtime gates
        // still apply; present metadata is authoritative and must not be ignored.
        require(supported.isEmpty() || backend in supported) { "$modality does not support $backend in this artifact" }
    }
    internal fun versionSupported(required: String, current: String): Boolean {
        fun parse(value: String): List<Int>? = value.removePrefix("v").split('.').takeIf { it.size == 3 }
            ?.map { it.toIntOrNull() ?: return null }
        val minimum = parse(required) ?: return false
        val actual = parse(current) ?: return false
        for (index in 0..2) {
            if (actual[index] != minimum[index]) return actual[index] > minimum[index]
        }
        return true
    }
}
