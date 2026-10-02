package com.halo.app.data.watch

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "continuation_watches",
    indices = [
        Index("runId"),
        Index("status"),
        Index("deduplicationKey")
    ]
)
data class ContinuationWatchEntity(
    @PrimaryKey val id: String,
    val runId: String?,
    val interactionId: String?,
    val conditionType: String,
    val conditionJson: String,
    val behaviorType: String,
    val behaviorJson: String,
    val presentationText: String?,
    val status: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val deduplicationKey: String?,
    val matchMode: String,
    val matchedAt: Long?,
    val claimState: String = "UNCLAIMED"
)
