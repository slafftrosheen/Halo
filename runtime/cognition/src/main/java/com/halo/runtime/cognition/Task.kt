package com.halo.runtime.cognition

import java.util.UUID
import kotlin.time.Duration

data class Precondition(val id: String, val check: suspend () -> Boolean)
data class Effect(val id: String, val expectedState: String)

enum class TaskState {
    PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
}

sealed interface TaskResult {
    data class Success(val message: String = "") : TaskResult
    data class Failure(val reason: String, val exception: Throwable? = null) : TaskResult
    data class Retry(val attempt: Int, val delayMs: Long) : TaskResult
    object Cancelled : TaskResult
    data class Partial(val completedItems: Int, val totalItems: Int) : TaskResult
}

sealed interface Task {
    val id: UUID
    val state: TaskState
    val preconditions: List<Precondition>
    val effects: List<Effect>
    val fallbackTasks: List<Task>
    val dependencies: List<Task>
    val isParallel: Boolean
}

data class DeviceTask(
    override val id: UUID = UUID.randomUUID(),
    override val state: TaskState = TaskState.PENDING,
    override val preconditions: List<Precondition> = emptyList(),
    override val effects: List<Effect> = emptyList(),
    override val fallbackTasks: List<Task> = emptyList(),
    override val dependencies: List<Task> = emptyList(),
    override val isParallel: Boolean = false,
    val action: String,
    val parameters: Map<String, String> = emptyMap()
) : Task

data class ApplicationTask(
    override val id: UUID = UUID.randomUUID(),
    override val state: TaskState = TaskState.PENDING,
    override val preconditions: List<Precondition> = emptyList(),
    override val effects: List<Effect> = emptyList(),
    override val fallbackTasks: List<Task> = emptyList(),
    override val dependencies: List<Task> = emptyList(),
    override val isParallel: Boolean = false,
    val packageName: String,
    val intent: String,
    val displayName: String = ""
) : Task

data class MediaTask(
    override val id: UUID = UUID.randomUUID(),
    override val state: TaskState = TaskState.PENDING,
    override val preconditions: List<Precondition> = emptyList(),
    override val effects: List<Effect> = emptyList(),
    override val fallbackTasks: List<Task> = emptyList(),
    override val dependencies: List<Task> = emptyList(),
    override val isParallel: Boolean = false,
    val mediaAction: String,
    val mediaId: String
) : Task

data class NavigationTask(
    override val id: UUID = UUID.randomUUID(),
    override val state: TaskState = TaskState.PENDING,
    override val preconditions: List<Precondition> = emptyList(),
    override val effects: List<Effect> = emptyList(),
    override val fallbackTasks: List<Task> = emptyList(),
    override val dependencies: List<Task> = emptyList(),
    override val isParallel: Boolean = false,
    val coordinates: String
) : Task

data class CommunicationTask(
    override val id: UUID = UUID.randomUUID(),
    override val state: TaskState = TaskState.PENDING,
    override val preconditions: List<Precondition> = emptyList(),
    override val effects: List<Effect> = emptyList(),
    override val fallbackTasks: List<Task> = emptyList(),
    override val dependencies: List<Task> = emptyList(),
    override val isParallel: Boolean = false,
    val targetId: String,
    val payload: String
) : Task

data class MemoryTask(
    override val id: UUID = UUID.randomUUID(),
    override val state: TaskState = TaskState.PENDING,
    override val preconditions: List<Precondition> = emptyList(),
    override val effects: List<Effect> = emptyList(),
    override val fallbackTasks: List<Task> = emptyList(),
    override val dependencies: List<Task> = emptyList(),
    override val isParallel: Boolean = false,
    val memoryAction: String
) : Task

data class WebTask(
    override val id: UUID = UUID.randomUUID(),
    override val state: TaskState = TaskState.PENDING,
    override val preconditions: List<Precondition> = emptyList(),
    override val effects: List<Effect> = emptyList(),
    override val fallbackTasks: List<Task> = emptyList(),
    override val dependencies: List<Task> = emptyList(),
    override val isParallel: Boolean = false,
    val url: String
) : Task

data class SystemTask(
    override val id: UUID = UUID.randomUUID(),
    override val state: TaskState = TaskState.PENDING,
    override val preconditions: List<Precondition> = emptyList(),
    override val effects: List<Effect> = emptyList(),
    override val fallbackTasks: List<Task> = emptyList(),
    override val dependencies: List<Task> = emptyList(),
    override val isParallel: Boolean = false,
    val command: String
) : Task
