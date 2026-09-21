package com.halo.app.data.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MemoryKind {
    PREFERENCE,
    FACT,
    PERSON,
    PLACE,
    DEVICE,
    PROJECT,
    WORKFLOW,
    INSTRUCTION
}

enum class MemoryScope {
    GLOBAL,
    PROJECT,
    DEVICE,
    CONVERSATION
}

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
    val status: String
)
