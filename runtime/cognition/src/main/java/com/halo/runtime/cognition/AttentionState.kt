package com.halo.runtime.cognition

import kotlinx.datetime.Instant
import com.halo.icl.ontology.Goal

data class AttentionState(
    val currentFocus: Goal?,
    val recentThoughts: List<Thought>,
    val currentTask: Task?,
    val failures: List<Throwable>,
    val lastUpdated: Instant
)
