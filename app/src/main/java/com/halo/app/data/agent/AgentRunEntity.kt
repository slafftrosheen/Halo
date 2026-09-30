package com.halo.app.data.agent

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "agent_runs", indices = [Index("parentRunId"), Index("rootRunId")])
data class AgentRunEntity(
    @PrimaryKey val id: String,
    val interactionId: String,
    val request: String,
    val startedAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val state: String,
    val failureReason: String? = null,
    val parentRunId: String? = null,
    val rootRunId: String? = null,
    val delegatedRole: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val delegationDepth: Int = 0,
    val pinnedProviderId: String? = null,
    val pinnedModelId: String? = null,
    val pinnedTargetLocal: Boolean? = null,
    val delegatedInputRefs: String? = null,
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
