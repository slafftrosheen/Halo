package com.halo.engines.interpretation.api

data class InterpretationResult(
    val interpretation: Interpretation,
    val matches: List<RuleMatch>
)
