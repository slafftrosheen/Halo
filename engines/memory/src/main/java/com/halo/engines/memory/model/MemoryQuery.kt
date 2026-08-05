package com.halo.runtime.cognition.memory

import java.util.UUID

data class MemoryQuery(
    val intent: String?,
    val entities: List<String> = emptyList(),
    val currentGoalId: UUID? = null,
    val contextTags: List<String> = emptyList()
)

