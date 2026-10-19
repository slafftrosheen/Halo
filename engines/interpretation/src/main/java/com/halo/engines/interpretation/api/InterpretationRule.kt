package com.halo.engines.interpretation.api

interface InterpretationRule {
    val priority: Int

    suspend fun match(context: InterpretationContext): RuleMatch?

    /**
     * Incremental speech is provisional. A native LLM must never run or trigger
     * tool admission merely because partial ASR happened to resemble a command.
     */
    suspend fun previewMatch(context: InterpretationContext): RuleMatch? = match(context)
}
