package com.halo.app.ui.screens.settings

/**
 * Provider ID constants for UI layer.
 * These must match the IDs defined in provider implementations.
 */
object ProviderIds {
    const val OPENROUTER = "openrouter"
    const val OPENAI = "openai"
    const val ANTHROPIC = "anthropic"
    const val GEMINI = "gemini"
    const val OLLAMA = "provider.ollama"

    /**
     * The only on-device provider ID. llama.cpp was retired; migration of a persisted
     * `provider.llamacpp.native` selection is handled once by the router, not by UI code.
     */
    const val LITERT = "provider.litert.local"
}
