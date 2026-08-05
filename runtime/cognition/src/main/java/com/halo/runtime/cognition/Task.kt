package com.halo.runtime.cognition

import java.util.UUID

enum class TaskState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

data class TaskStep(
    val description: String,
    val completed: Boolean
)

data class Task(
    val id: UUID,
    val goal: Goal,
    val state: TaskState,
    val steps: MutableList<TaskStep>
)
