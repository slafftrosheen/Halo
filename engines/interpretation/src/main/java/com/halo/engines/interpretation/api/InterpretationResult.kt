package com.halo.engines.interpretation.api

import com.halo.engines.interpretation.RuleExecution

data class InterpretationResult(
    val interpretation: Interpretation,
    val matches: List<RuleMatch>,
    val executions: List<RuleExecution> = emptyList()
)
