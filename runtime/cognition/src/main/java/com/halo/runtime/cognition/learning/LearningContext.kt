package com.halo.runtime.cognition.learning

import com.halo.runtime.cognition.memory.Episode
import com.halo.runtime.cognition.memory.Reflection
import com.halo.runtime.cognition.memory.Memory
import com.halo.runtime.cognition.DeviceState

data class ExecutionResult(
    val success: Boolean,
    val latencyMs: Long,
    val error: String?
)

data class LearningContext(
    val episode: Episode?,
    val reflection: Reflection?,
    val memories: List<Memory>,
    val execution: ExecutionResult?,
    val userFeedback: Feedback?,
    val environment: DeviceState
)

