package com.halo.runtime.cognition

import com.halo.runtime.cognition.workingmemory.WorkingMemory
import com.halo.runtime.cognition.blackboard.Interpretation
import kotlinx.datetime.Instant
import com.halo.icl.ontology.Goal

data class ReasoningContext(
    val interpretation: Interpretation,
    val goals: List<Goal>,
    val memories: List<Any>, // Typed to Any for now since Memory model not fully defined in request
    val deviceState: Any,    // Typed to Any
    val environment: Any,    // Typed to Any
    val conversation: Any,   // Typed to Any
    val timestamp: Instant
)
