package com.halo.app.data.agent

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

enum class AgentRunState {
    PLANNING,
    WAITING_FOR_APPROVAL,
    EXECUTING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(tableName = "agent_runs")
data class AgentRunEntity(
    @PrimaryKey val id: String,
    val request: String,
    val startedAt: Long,
    val completedAt: Long?,
    val state: String
)

@Entity(
    tableName = "agent_steps",
    foreignKeys = [
        ForeignKey(
            entity = AgentRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AgentStepEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val stepType: String,
    val payload: String,
    val timestamp: Long
)
