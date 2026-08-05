package com.halo.runtime.cognition.blackboard

data class Confidence(
    val lexical: Float,
    val contextual: Float,
    val semantic: Float
) {
    val overall: Float
        get() = lexical * contextual * semantic

    companion object {
        val None = Confidence(
            lexical = 0f,
            contextual = 0f,
            semantic = 0f
        )
    }
}
