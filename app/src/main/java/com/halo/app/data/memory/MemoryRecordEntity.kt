package com.halo.app.data.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

typealias MemoryKind = com.halo.runtime.cognition.memory.MemoryKind
typealias MemoryScope = com.halo.runtime.cognition.memory.MemoryScope

enum class MemoryStatus {
    ACTIVE,
    SUPERSEDED,
    FORGOTTEN
}

@Entity(tableName = "memory_records")
data class MemoryRecordEntity(
    @PrimaryKey val id: String,
    val content: String,
    val kind: String,
    val scope: String,
    val scopeId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceObjectId: String?,
    val sourceRevision: Int?,
    val confidence: Float?,
    val status: String,
    @androidx.room.ColumnInfo(defaultValue = "'IMPORT'") val origin: String = "IMPORT",
    @androidx.room.ColumnInfo(defaultValue = "'[]'") val evidenceJson: String = "[]",
    val sourceEpisodeId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val evidenceCount: Int = 0,
    val lastConfirmedAt: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val usageCount: Long = 0,
    val lastUsedAt: Long? = null,
    val validUntil: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "''") val fingerprint: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val userConfirmed: Boolean = false
)
