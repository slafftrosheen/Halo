package com.halo.app.data.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_revisions")
data class MemoryRevisionEntity(
    @PrimaryKey val id: String,
    val memoryId: String,
    val previousContent: String,
    val newContent: String,
    val changedAt: Long,
    val reason: String
)
