package com.halo.engines.interpretation.rules

import com.halo.engines.interpretation.api.Interpretation
import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.interpretation.api.LlmInterpretationClient
import com.halo.engines.interpretation.api.InterpretationRule
import com.halo.engines.interpretation.api.RuleMatch

class LlmRule(
    private val client: LlmInterpretationClient
) : InterpretationRule {

    override val priority: Int = 10

    override suspend fun match(
        context: InterpretationContext
    ): RuleMatch? = client.interpret(context)
}
