package com.halo.app.data.routine

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val triggerJson: String,
    val conditionsJson: String,
    val actionsJson: String,
    val schemaVersion: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
    val lastRunAt: Long?,
    val nextRunAt: Long?
)

@Entity(tableName = "routine_runs")
data class RoutineRunEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val agentRunId: String,
    val triggerAt: Long,
    val state: String
)
