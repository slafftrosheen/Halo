package com.halo.app.data.workspace

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.halo.app.data.notebook.NoteEntity
import kotlinx.coroutines.flow.Flow

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

@Entity(
    tableName = "note_attachments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["objectId"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WorkspaceAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index(value = ["assetId"], unique = true)],
)
data class NoteAttachmentEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val assetId: String,
    val position: Int,
    val caption: String?,
    val createdAtEpochMs: Long,
)

data class NoteAttachment(
    val id: String,
    val noteId: String,
    val assetId: String,
    val position: Int,
    val caption: String?,
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
)

@Dao
interface WorkspaceAssetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAsset(entity: WorkspaceAssetEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttachment(entity: NoteAttachmentEntity)

    @Query(
        """
        SELECT a.id, a.noteId, a.assetId, a.position, a.caption,
               s.type, s.mimeType, s.storageType, s.pathOrUri,
               s.permissionPersisted, s.displayName, s.bytes,
               s.width, s.height, s.durationMs
        FROM note_attachments a
        INNER JOIN workspace_assets s ON s.id = a.assetId
        WHERE a.noteId = :noteId
        ORDER BY a.position ASC, a.createdAtEpochMs ASC
        """
    )
    fun observeAttachments(noteId: String): Flow<List<NoteAttachment>>
    @Query("""SELECT a.id,a.noteId,a.assetId,a.position,a.caption,s.type,s.mimeType,s.storageType,s.pathOrUri,
        s.permissionPersisted,s.displayName,s.bytes,s.width,s.height,s.durationMs FROM note_attachments a
        INNER JOIN workspace_assets s ON s.id=a.assetId WHERE a.noteId=:noteId AND a.id=:attachmentId""")
    suspend fun attachment(noteId: String, attachmentId: String): NoteAttachment?

    @Query("SELECT EXISTS(SELECT 1 FROM note_attachments a INNER JOIN workspace_assets s ON s.id=a.assetId WHERE a.noteId=:noteId AND s.type='IMAGE')")
    suspend fun hasImageAttachment(noteId: String): Boolean

    /** Metadata only; callers must opt in to each returned attachment before reading its bytes. */
    @Query(
        """
        SELECT a.id, a.noteId, a.assetId, a.position, a.caption,
               s.type, s.mimeType, s.storageType, s.pathOrUri,
               s.permissionPersisted, s.displayName, s.bytes,
               s.width, s.height, s.durationMs
        FROM note_attachments a
        INNER JOIN workspace_assets s ON s.id = a.assetId
        WHERE s.type = 'IMAGE'
        ORDER BY a.createdAtEpochMs DESC
        """
    )
    fun observeImageAttachments(): Flow<List<NoteAttachment>>

    /** Metadata only; audio is decoded only after an exact attachment is selected. */
    @Query(
        """
        SELECT a.id, a.noteId, a.assetId, a.position, a.caption,
               s.type, s.mimeType, s.storageType, s.pathOrUri,
               s.permissionPersisted, s.displayName, s.bytes,
               s.width, s.height, s.durationMs
        FROM note_attachments a
        INNER JOIN workspace_assets s ON s.id = a.assetId
        WHERE s.mimeType LIKE 'audio/%'
        ORDER BY a.createdAtEpochMs DESC
        """
    )
    fun observeAudioAttachments(): Flow<List<NoteAttachment>>

    /** Video metadata only; frame extraction requires an explicit local-search selection. */
    @Query(
        """
        SELECT a.id, a.noteId, a.assetId, a.position, a.caption,
               s.type, s.mimeType, s.storageType, s.pathOrUri,
               s.permissionPersisted, s.displayName, s.bytes,
               s.width, s.height, s.durationMs
        FROM note_attachments a
        INNER JOIN workspace_assets s ON s.id = a.assetId
        WHERE s.mimeType LIKE 'video/%'
        ORDER BY a.createdAtEpochMs DESC
        """
    )
    fun observeVideoAttachments(): Flow<List<NoteAttachment>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM note_attachments WHERE noteId = :noteId")
    suspend fun nextPosition(noteId: String): Int

    @Query("DELETE FROM note_attachments WHERE id = :attachmentId AND noteId = :noteId")
    suspend fun deleteAttachment(noteId: String, attachmentId: String): Int

    @Query("SELECT assetId FROM note_attachments WHERE id = :attachmentId AND noteId = :noteId LIMIT 1")
    suspend fun attachmentAssetId(noteId: String, attachmentId: String): String?

    @Query("DELETE FROM workspace_assets WHERE id = :assetId")
    suspend fun deleteAsset(assetId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM note_attachments a
        INNER JOIN workspace_assets s ON s.id = a.assetId
        WHERE a.noteId = :noteId AND s.pathOrUri = :pathOrUri
        """
    )
    suspend fun attachmentAssetCount(noteId: String, pathOrUri: String): Int
}
