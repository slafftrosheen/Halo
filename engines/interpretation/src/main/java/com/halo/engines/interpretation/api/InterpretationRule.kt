package com.halo.engines.interpretation.api

interface InterpretationRule {
    val priority: Int

    suspend fun applies(
        context: InterpretationContext
    ): Boolean

    suspend fun interpret(
        context: InterpretationContext
    ): Interpretation?
}
