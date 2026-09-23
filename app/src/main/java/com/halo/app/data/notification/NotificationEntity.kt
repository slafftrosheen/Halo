package com.halo.app.data.notification

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class NotificationAccessPolicy {
    ALLOW,
    SUMMARIZE_ONLY,
    HIDE_CONTENT,
    BLOCK,
}

@Entity(
    tableName = "notifications",
    indices = [
        Index("packageName"),
        Index("postedAt"),
        Index("active")
    ]
)
data class NotificationEntity(
    @PrimaryKey val key: String,
    val packageName: String,
    val appLabel: String?,
    val title: String?,
    val text: String?,
    val category: String?,
    val postedAt: Long,
    val removedAt: Long?,
    val active: Boolean,
    val sensitivity: String = "NORMAL"
)

data class UntrustedNotificationContent(
    val title: String?,
    val body: String?,
)
