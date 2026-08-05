package com.halo.runtime.cognition

import kotlinx.datetime.Instant

data class AttentionState(
    val currentFocus: Goal?,
    val recentThoughts: List<Thought>,
    val currentTask: Task?,
    val failures: List<Throwable>,
    val lastUpdated: Instant
)
