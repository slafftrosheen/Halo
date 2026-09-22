package com.halo.providers.llamacpp

interface LlamaArchitectureSupport {
    fun supports(architecture: String?): Boolean
}

/** Architectures exercised by the bundled llama.cpp Android binding. */
object BundledLlamaArchitectureSupport : LlamaArchitectureSupport {
    private val supported = setOf(
        "llama", "qwen2", "qwen3", "gemma", "gemma2", "gemma3",
        "phi3", "mistral", "granite", "smollm3", "gpt2"
    )
    override fun supports(architecture: String?): Boolean =
        architecture?.lowercase() in supported
}
