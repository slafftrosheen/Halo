package com.halo.app.data.agent

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AgentRunState {
    CREATED,
    PLANNING,

    WAITING_FOR_CLARIFICATION,
    WAITING_FOR_APPROVAL,
    WAITING_FOR_CAPABILITY,

    EXECUTING,
    SUSPENDED,

    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
}

enum class ActionTruth {
    PROPOSED,
    AUTHORIZED,
    DISPATCHED,
    ACKNOWLEDGED,
    CONFIRMED,
    UNKNOWN,
    FAILED,
}

@Entity(tableName = "agent_runs")
data class AgentRunEntity(
    @PrimaryKey val id: String,
    val interactionId: String,
    val request: String,
    val startedAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val state: String,
    val failureReason: String? = null
)

@Entity(
    tableName = "agent_steps",
    indices = [
        Index("runId"),
        Index("interactionId"),
        Index("idempotencyKey")
    ]
)
data class AgentStepEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val interactionId: String,
    val ordinal: Int,
    val type: String,
    val schemaVersion: Int = 2,
    val payloadJson: String,
    val idempotencyKey: String?,
    val state: String,
    val createdAt: Long,
    val completedAt: Long?
)
