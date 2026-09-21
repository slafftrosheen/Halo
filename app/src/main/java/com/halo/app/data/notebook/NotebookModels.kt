package com.halo.app.data.notebook

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@JvmInline
value class WorkspaceObjectId(val value: String)

enum class WorkspaceObjectType {
    NOTE,
    FILE,
    CONVERSATION,
    MEMORY,
    MEDIA,
    ARTIFACT
}

enum class WorkspaceObjectSource {
    USER_CREATED,
    QUICK_CAPTURE,
    IMPORT,
    HALO_GENERATED,
    DEVICE_CAPTURE,
    CONVERSATION,
    SHARE_INTENT
}

enum class WorkspaceObjectLifecycle { ACTIVE, ARCHIVED, TRASHED }
typealias WorkspaceLifecycle = WorkspaceObjectLifecycle

enum class WorkspaceSource { QUICK_NOTE, USER_CREATED, DEVICE_CAPTURE, IMPORT }

data class WorkspaceProvenance(
    val sourceType: WorkspaceObjectSource,
    val sourceObjectId: String? = null,
    val sourceDeviceId: String? = null,
    val sourceRevision: String? = null,
    val capturedAt: Long? = null
)

data class WorkspaceObject(
    val id: WorkspaceObjectId,
    val type: WorkspaceObjectType,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val source: WorkspaceObjectSource,
    val lifecycle: WorkspaceObjectLifecycle,
    val inInbox: Boolean,
    val contentRef: String,
    val provenance: WorkspaceProvenance? = null
)

@Entity(
    tableName = "workspace_objects",
    indices = [
        Index(value = ["type"]),
        Index(value = ["lifecycle", "updatedAt"])
    ]
)
data class WorkspaceObjectEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val source: String,
    val lifecycle: String,
    val inInbox: Boolean,
    val indexingState: String,
    val contentRef: String
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceObjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["objectId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NoteEntity(
    @PrimaryKey val objectId: String,
    val body: String,
    val revision: Int
)

@Entity(
    tableName = "note_drafts",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceObjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["objectId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NoteDraftEntity(
    @PrimaryKey val objectId: String,
    val body: String,
    val updatedAt: Long
)

data class NoteDraft(
    val objectId: String,
    val body: String,
    val updatedAt: Long
)

data class NotebookNote(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val source: WorkspaceSource,
    val lifecycle: WorkspaceLifecycle,
    val inInbox: Boolean,
    val revision: Int
)

data class NotebookNoteRow(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val source: String,
    val lifecycle: String,
    val inInbox: Boolean,
    val revision: Int
) {
    fun toDomain() = NotebookNote(
        id = id,
        title = title,
        body = body,
        createdAt = createdAt,
        updatedAt = updatedAt,
        source = WorkspaceSource.valueOf(source),
        lifecycle = WorkspaceLifecycle.valueOf(lifecycle),
        inInbox = inInbox,
        revision = revision
    )
}
