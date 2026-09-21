package com.halo.app.data.workspace

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.halo.app.data.notebook.WorkspaceObjectEntity

enum class FilePermissionMode {
    PERSISTABLE_URI,
    OWNED_COPY,
    READ_ONLY
}

enum class ContentOwnership { HALO_OWNED, EXTERNAL_REFERENCE }

enum class WorkspaceFileAvailability { AVAILABLE, NEEDS_RELINK, MISSING, OWNED_LOCAL }

@Entity(
    tableName = "workspace_files",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceObjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["objectId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WorkspaceFileEntity(
    @PrimaryKey val objectId: String,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val modifiedAt: Long?,
    val ownedCopyPath: String?,
    val permissionPersisted: Boolean,
    @ColumnInfo(defaultValue = "'EXTERNAL_REFERENCE'") val ownership: String = ContentOwnership.EXTERNAL_REFERENCE.name,
    @ColumnInfo(defaultValue = "'AVAILABLE'") val availability: String = WorkspaceFileAvailability.AVAILABLE.name,
    val sha256: String? = null,
)
