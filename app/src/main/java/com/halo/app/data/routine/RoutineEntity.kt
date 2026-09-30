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
    val nextRunAt: Long?,
    @androidx.room.ColumnInfo(defaultValue = "'{\"type\":\"silent\"}'") val deliveryJson: String = "{\"type\":\"silent\"}",
    val grantDigest: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val minimumIntervalSeconds: Long = 0
)

@Entity(tableName = "routine_runs")
data class RoutineRunEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val agentRunId: String,
    val triggerAt: Long,
    val state: String,
    @androidx.room.ColumnInfo(defaultValue = "'SCHEDULED'") val source: String = "SCHEDULED",
    val signalId: String? = null,
    val rootOccurrenceId: String? = null,
    val causedByOccurrenceId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val actionsCompleted: Int = 0,
    @androidx.room.ColumnInfo(defaultValue = "0") val actionsTotal: Int = 0,
    val finishedAt: Long? = null,
    val reason: String? = null,
    val deliveryOutcome: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "1") val coalescedCount: Int = 1
)
