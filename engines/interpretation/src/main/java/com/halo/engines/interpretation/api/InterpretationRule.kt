package com.halo.engines.interpretation.api

interface InterpretationRule {
    val priority: Int

    suspend fun match(
        context: InterpretationContext
    ): RuleMatch?
}
