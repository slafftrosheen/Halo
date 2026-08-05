package com.halo.engines.interpretation.api

interface LlmInterpretationClient {
    suspend fun interpret(
        context: InterpretationContext
    ): Interpretation?
}
