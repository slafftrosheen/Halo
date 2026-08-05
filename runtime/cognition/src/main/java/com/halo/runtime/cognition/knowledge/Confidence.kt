package com.halo.runtime.cognition.knowledge

data class Confidence(
    val lexical: Float,
    val contextual: Float,
    val semantic: Float
) {
    val overall: Float
        get() = (lexical + contextual + semantic) / 3f

    companion object {
        val None = Confidence(
            lexical = 0f,
            contextual = 0f,
            semantic = 0f
        )
    }
}
