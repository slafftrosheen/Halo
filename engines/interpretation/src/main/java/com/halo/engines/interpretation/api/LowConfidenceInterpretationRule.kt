package com.halo.engines.interpretation.api

interface LowConfidenceInterpretationRule {
    val priority: Int

    suspend fun applies(
        context: InterpretationContext,
        current: Interpretation
    ): Boolean

    suspend fun interpret(
        context: InterpretationContext,
        current: Interpretation
    ): RuleMatch?
}
