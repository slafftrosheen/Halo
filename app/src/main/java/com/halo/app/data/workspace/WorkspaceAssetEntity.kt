package com.halo.app.data.workspace

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspace_assets")
data class WorkspaceAssetEntity(
    @PrimaryKey val id: String,
    val objectId: String,
    val type: String,
    val mimeType: String,
    val storageType: String,
    val pathOrUri: String,
    val permissionPersisted: Boolean,
    val displayName: String?,
    val bytes: Long?,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val createdAtEpochMs: Long,
)
