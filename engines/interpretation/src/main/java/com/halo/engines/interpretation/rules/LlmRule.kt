package com.halo.engines.interpretation.rules

import com.halo.engines.interpretation.api.Interpretation
import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.interpretation.api.LlmInterpretationClient
import com.halo.engines.interpretation.api.LowConfidenceInterpretationRule
import com.halo.engines.interpretation.api.RuleMatch

class LlmRule(
    private val client: LlmInterpretationClient,
    private val confidenceThreshold: Float = 0.7f
) : LowConfidenceInterpretationRule {
    init {
        require(confidenceThreshold in 0f..1f) {
            "confidenceThreshold must be between 0 and 1"
        }
    }

    override val priority: Int = 10

    override suspend fun applies(
        context: InterpretationContext,
        current: Interpretation
    ): Boolean = current.confidence.overall < confidenceThreshold

    override suspend fun interpret(
        context: InterpretationContext,
        current: Interpretation
    ): RuleMatch? = client.interpret(context)
}
