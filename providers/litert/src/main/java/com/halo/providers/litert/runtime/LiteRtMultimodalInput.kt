package com.halo.providers.litert.runtime

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.halo.sdk.provider.RuntimeGenerationConfig

/** Validate before JNI: oversized or unsupported input must not allocate a native conversation. */
internal object LiteRtMultimodalInput {
    const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
    const val MAX_AUDIO_BYTES = LiteRtAudioInput.MAX_BYTES

    fun validate(config: RuntimeGenerationConfig, visionEnabled: Boolean, audioEnabled: Boolean) {
        config.imageBytes?.let {
            require(visionEnabled) { "Image input is not enabled for this model" }
            require(it.isNotEmpty() && it.size <= MAX_IMAGE_BYTES) { "Image input must contain 1–10485760 bytes" }
        }
        config.audioBytes?.let {
            require(audioEnabled) { "Audio input is not enabled for this model" }
            require(it.isNotEmpty() && it.size <= MAX_AUDIO_BYTES) { "Audio input must contain 1–20971520 bytes" }
        }
    }

    fun contents(config: RuntimeGenerationConfig): Contents = Contents.of(buildList<Content> {
        // Keep media before the question, as in the upstream multimodal example.
        config.imageBytes?.let { add(Content.ImageBytes(it)) }
        config.audioBytes?.let { add(Content.AudioBytes(it)) }
        if (config.prompt.isNotEmpty()) add(Content.Text(config.prompt))
    })
}
