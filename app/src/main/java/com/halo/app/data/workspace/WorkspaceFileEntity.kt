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

/** Canonical observed row identity. Availability is a separate state, never new content authority. */
fun WorkspaceFileEntity.contentVersion(): String = sha256 ?: ("workspace-file:" + java.security.MessageDigest.getInstance("SHA-256").digest(
    listOf(uri, mimeType, sizeBytes?.toString().orEmpty(), modifiedAt?.toString().orEmpty(), ownership,
        ownedCopyPath.orEmpty(), permissionPersisted.toString()).joinToString("\u0000").toByteArray(Charsets.UTF_8)
).joinToString("") { "%02x".format(it) })

fun WorkspaceFileEntity.isReadable(): Boolean = availability in setOf(
    WorkspaceFileAvailability.AVAILABLE.name, WorkspaceFileAvailability.OWNED_LOCAL.name)
