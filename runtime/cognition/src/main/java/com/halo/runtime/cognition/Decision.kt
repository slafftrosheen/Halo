package com.halo.runtime.cognition

import com.halo.icl.ontology.Goal

data class Decision(
    val goal: Goal,
    val rationale: String,
    val confidence: Float
)
