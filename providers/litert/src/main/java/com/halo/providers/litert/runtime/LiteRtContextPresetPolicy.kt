package com.halo.providers.litert.runtime

/**
 * Explicit developer-only context allocations for performance experiments.
 * Smaller max-token allocations can change speed and memory, but also reduce useful history.
 * No automatic downshift, no silent revision of user preferences.
 */
object LiteRtContextPresetPolicy {
    val PRESETS = listOf(1024, 2048, 4096, 8192)

    fun choicesFor(artifactMaxContextTokens: Int): List<Int> =
        PRESETS.filter { it <= artifactMaxContextTokens }

    fun isAllowed(tokens: Int?, artifactMaxContextTokens: Int): Boolean =
        tokens == null || (tokens in PRESETS && tokens <= artifactMaxContextTokens)
}
