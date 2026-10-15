package com.halo.app.ai

/** Official setup destinations; never contain credentials or redirect query parameters. */
fun providerKeyPage(providerId: String): String? = when (providerId) {
    "openai" -> "https://platform.openai.com/api-keys"
    "anthropic" -> "https://platform.claude.com/settings/keys"
    "gemini" -> "https://aistudio.google.com/api-keys"
    "openrouter" -> "https://openrouter.ai/settings/keys"
    "groq" -> "https://console.groq.com/keys"
    "cerebras" -> "https://cloud.cerebras.ai/platform/api-keys"
    "xai" -> "https://console.x.ai/"
    "opencode.zen" -> "https://opencode.ai/auth"
    else -> null
}
