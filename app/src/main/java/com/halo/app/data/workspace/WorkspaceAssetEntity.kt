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

    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId ORDER BY position, createdAtEpochMs, id")
    suspend fun privateAttachmentLinks(noteId: String): List<NoteAttachmentEntity>

    @Query("""SELECT n.* FROM note_attachments n JOIN workspace_assets a ON a.id = n.assetId
        WHERE a.objectId = :noteId ORDER BY n.position, n.createdAtEpochMs, n.id""")
    suspend fun privateBackingLinks(noteId: String): List<NoteAttachmentEntity>

    @Query("""SELECT n.noteId FROM note_attachments n JOIN workspace_assets a ON a.id = n.assetId
        WHERE a.objectId = :noteId AND n.noteId <> :noteId
        UNION SELECT a.objectId FROM note_attachments n JOIN workspace_assets a ON a.id = n.assetId
        WHERE n.noteId = :noteId AND a.objectId <> :noteId""")
    suspend fun privateLinkedNoteOwners(noteId: String): List<String>

    @Query("SELECT DISTINCT noteId FROM note_attachments WHERE assetId IN (:assetIds)")
    suspend fun linkedAttachmentNoteIds(assetIds: List<String>): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM note_attachments WHERE assetId = :assetId AND noteId <> :noteId)")
    suspend fun attachmentUsedByAnotherNote(assetId: String, noteId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM workspace_assets WHERE pathOrUri IN (:addresses)) OR EXISTS(SELECT 1 FROM workspace_files WHERE uri IN (:addresses) OR ownedCopyPath IN (:addresses))")
    suspend fun hasStoredAssetPath(addresses: List<String>): Boolean

    @Query("SELECT * FROM workspace_assets")
    suspend fun privateAssetBackings(): List<WorkspaceAssetEntity>

    @Query("SELECT id FROM workspace_assets WHERE objectId = :noteId UNION SELECT id FROM note_attachments WHERE noteId = :noteId")
    suspend fun privateOwnedAttachmentIds(noteId: String): List<String>

    @Query("""SELECT
        EXISTS(SELECT 1 FROM notes WHERE
            (instr(:protectedOwners, '|' || objectId || '|') = 0 AND (instr(body, :assetId) > 0 OR instr(body, :linkId) > 0)) OR
            (:publicBacking = 0 AND instr(:rebindingOwners, '|' || objectId || '|') = 0 AND instr(body, :basename) > 0)) OR
        EXISTS(SELECT 1 FROM note_blocks WHERE
            (instr(:protectedOwners, '|' || noteId || '|') = 0 AND (refObjectId IN (:assetId, :linkId) OR instr(text, :assetId) > 0 OR instr(text, :linkId) > 0)) OR
            (:publicBacking = 0 AND instr(:rebindingOwners, '|' || noteId || '|') = 0 AND instr(text, :basename) > 0)) OR
        EXISTS(SELECT 1 FROM note_drafts WHERE
            (instr(:protectedOwners, '|' || objectId || '|') = 0 AND (instr(body, :assetId) > 0 OR instr(body, :linkId) > 0 OR
                instr(structureJson, :assetId) > 0 OR instr(structureJson, :linkId) > 0)) OR
            (:publicBacking = 0 AND instr(:rebindingOwners, '|' || objectId || '|') = 0 AND
                (instr(body, :basename) > 0 OR instr(structureJson, :basename) > 0))) OR
        EXISTS(SELECT 1 FROM canvas_nodes WHERE
            (instr(:protectedOwners, '|' || canvasId || '|') = 0 AND (refObjectId IN (:assetId, :linkId) OR instr(text, :assetId) > 0 OR instr(text, :linkId) > 0)) OR
            (:publicBacking = 0 AND instr(text, :basename) > 0)) OR
        EXISTS(SELECT 1 FROM object_provenance WHERE
            (instr(:protectedOwners, '|' || objectId || '|') = 0 AND (sourceId IN (:assetId, :linkId) OR instr(metadata, :assetId) > 0 OR instr(metadata, :linkId) > 0)) OR
            (:publicBacking = 0 AND instr(:rebindingOwners, '|' || objectId || '|') = 0 AND instr(metadata, :basename) > 0)) OR
        EXISTS(SELECT 1 FROM study_sessions WHERE
            (instr(:protectedOwners, '|' || id || '|') = 0 AND (instr(sourcesJson, :assetId) > 0 OR instr(sourcesJson, :linkId) > 0)) OR
            (:publicBacking = 0 AND instr(sourcesJson, :basename) > 0)) OR
        EXISTS(SELECT 1 FROM agent_steps WHERE
            (instr(:protectedOwners, '|' || runId || '|') = 0 AND (instr(payloadJson, :assetId) > 0 OR instr(payloadJson, :linkId) > 0)) OR
            (:publicBacking = 0 AND instr(payloadJson, :basename) > 0))
        """)
    suspend fun privateAttachmentHasPublicReferences(protectedOwners: String, rebindingOwners: String, assetId: String,
        linkId: String?, basename: String, publicBacking: Boolean): Boolean

    @Query("UPDATE notes SET body = :replacement WHERE objectId = :noteId AND body = :expected")
    suspend fun rebindRestoredNoteBody(noteId: String, expected: String, replacement: String): Int

    @Query("UPDATE note_blocks SET text = :replacement, presentation = :presentation WHERE noteId = :noteId AND id = :blockId AND text = :expected")
    suspend fun rebindRestoredBlockText(noteId: String, blockId: String, expected: String, replacement: String, presentation: String?): Int

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
