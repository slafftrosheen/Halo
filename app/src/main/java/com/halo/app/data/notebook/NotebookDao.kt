package com.halo.app.data.notebook

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.halo.app.data.workspace.WorkspaceAssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("""SELECT w.id, w.type, w.title, COALESCE(n.body, m.content, t.userText || ' ' || t.assistantText, w.title) AS snippet
        FROM workspace_objects w LEFT JOIN notes n ON n.objectId = w.id
        LEFT JOIN memory_records m ON m.id = w.id LEFT JOIN conversation_turns t ON t.conversationId = w.id
        WHERE w.lifecycle = 'ACTIVE' ORDER BY w.updatedAt DESC, w.id, t.timestamp LIMIT :limit OFFSET :offset""")
    suspend fun localSearchCandidates(limit: Int = 64, offset: Int = 0): List<WorkspaceSearchRow>

    @Query("""SELECT c.canvasId, c.id AS nodeId, w.title AS boardTitle, c.type AS nodeType,
        c.refObjectId, r.type AS sourceType, r.title AS sourceTitle,
        CASE WHEN c.refObjectId IS NOT NULL THEN n.body ELSE c.text END AS content,
        (SELECT group_concat(tag.text, ' ') FROM canvas_edges e JOIN canvas_nodes tag ON tag.id = e.toNodeId
            WHERE e.fromNodeId = c.id AND e.label = 'tag' AND tag.type = 'TAG') AS tags
        FROM canvas_nodes c JOIN workspace_objects w ON w.id = c.canvasId
        LEFT JOIN workspace_objects r ON r.id = c.refObjectId
        LEFT JOIN notes n ON n.objectId = c.refObjectId
        WHERE w.lifecycle = 'ACTIVE' AND c.type NOT IN ('SKETCH', 'INK_SELECTION') AND
        (c.refObjectId IS NULL OR r.lifecycle = 'ACTIVE') ORDER BY c.id LIMIT :limit OFFSET :offset""")
    suspend fun canvasSearchCandidates(limit: Int = 64, offset: Int = 0): List<CanvasSearchRow>
    @Query(
        """
        SELECT w.id AS id, w.type AS type, w.title AS title,
               COALESCE(
                   (SELECT SUBSTR(n.body, 1, 160) FROM notes n WHERE n.objectId = w.id),
                   (SELECT SUBSTR(m.content, 1, 160) FROM memory_records m WHERE m.id = w.id),
                   (SELECT SUBSTR(c.userText || ' ' || c.assistantText, 1, 160)
                    FROM conversation_turns c WHERE c.conversationId = w.id ORDER BY c.timestamp DESC LIMIT 1),
                   w.title
               ) AS snippet
        FROM workspace_objects w
        WHERE w.title LIKE :pattern ESCAPE '\'
           OR EXISTS (SELECT 1 FROM notes n WHERE n.objectId = w.id AND n.body LIKE :pattern ESCAPE '\')
           OR EXISTS (SELECT 1 FROM memory_records m WHERE m.id = w.id AND m.content LIKE :pattern ESCAPE '\')
           OR EXISTS (SELECT 1 FROM conversation_turns c WHERE c.conversationId = w.id
                      AND (c.userText LIKE :pattern ESCAPE '\' OR c.assistantText LIKE :pattern ESCAPE '\'))
        ORDER BY w.updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchWorkspaceObjects(pattern: String, limit: Int): List<WorkspaceSearchRow>

    @Query(
        """
        SELECT workspace_objects.id, workspace_objects.title, notes.body,
               workspace_objects.createdAt, workspace_objects.updatedAt,
               workspace_objects.source, workspace_objects.lifecycle,
               workspace_objects.inInbox, notes.revision
        FROM workspace_objects
        INNER JOIN notes ON notes.objectId = workspace_objects.id
        WHERE workspace_objects.type = 'NOTE'
          AND workspace_objects.lifecycle = :lifecycle
          AND (:inboxOnly = 0 OR workspace_objects.inInbox = 1)
          AND (:searchPattern = ''
               OR workspace_objects.title LIKE :searchPattern ESCAPE '\'
               OR notes.body LIKE :searchPattern ESCAPE '\')
        ORDER BY workspace_objects.updatedAt DESC
        """
    )
    fun observeNotes(
        searchPattern: String,
        lifecycle: String,
        inboxOnly: Boolean
    ): Flow<List<NotebookNoteRow>>

    @Query(
        """
        SELECT workspace_objects.id, workspace_objects.title, notes.body,
               workspace_objects.createdAt, workspace_objects.updatedAt,
               workspace_objects.source, workspace_objects.lifecycle,
               workspace_objects.inInbox, notes.revision
        FROM workspace_objects
        INNER JOIN notes ON notes.objectId = workspace_objects.id
        WHERE workspace_objects.id = :id AND workspace_objects.type = 'NOTE'
        LIMIT 1
        """
    )
    suspend fun getNote(id: String): NotebookNoteRow?

    @Query("SELECT * FROM note_drafts WHERE objectId = :id LIMIT 1")
    suspend fun getDraft(id: String): NoteDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkspaceObject(entity: WorkspaceObjectEntity)

    @Query("UPDATE workspace_objects SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long): Int

    @Query("SELECT * FROM workspace_objects WHERE id = :id LIMIT 1")
    suspend fun getWorkspaceObject(id: String): WorkspaceObjectEntity?

    @Query(
        """
        SELECT * FROM workspace_objects
        WHERE lifecycle = :lifecycle
          AND (:inboxOnly = 0 OR inInbox = 1)
          AND (:searchPattern = '' OR title LIKE :searchPattern ESCAPE '\')
        ORDER BY updatedAt DESC
        """
    )
    fun observeWorkspaceObjects(
        searchPattern: String,
        lifecycle: String,
        inboxOnly: Boolean
    ): Flow<List<WorkspaceObjectEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertObject(entity: WorkspaceObjectEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNote(entity: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(entity: NoteDraftEntity)

    @Transaction
    suspend fun insertNoteObject(objectEntity: WorkspaceObjectEntity, noteEntity: NoteEntity) {
        insertObject(objectEntity)
        insertNote(noteEntity)
    }

    @Query(
        """
        UPDATE workspace_objects
        SET title = :title, updatedAt = :updatedAt
        WHERE id = :id AND type = 'NOTE' AND lifecycle != 'TRASHED'
        """
    )
    suspend fun updateObjectForNote(id: String, title: String, updatedAt: Long): Int

    /**
     * Body-only write guard: refuses trashed notes at the storage layer, not only in the UI. Notes
     * that own canonical blocks are written through [NotebookDocumentRepository] instead.
     */
    @Query(
        """
        UPDATE notes SET body = :body, revision = revision + 1
        WHERE objectId = :id
          AND EXISTS (SELECT 1 FROM workspace_objects w WHERE w.id = :id AND w.lifecycle != 'TRASHED')
        """
    )
    suspend fun updateNoteBody(id: String, body: String): Int

    @Query("SELECT COUNT(*) FROM note_blocks WHERE noteId = :id")
    suspend fun countBlocks(id: String): Int


    @Query("DELETE FROM note_drafts WHERE objectId = :id")
    suspend fun deleteDraft(id: String): Int

    /**
     * A legacy body-only save may only acknowledge a legacy draft. A newer structural draft is
     * never cleared by an older save acknowledgement.
     */
    @Query("DELETE FROM note_drafts WHERE objectId = :id AND sequence = 0")
    suspend fun deleteLegacyDraft(id: String): Int

    @Transaction
    suspend fun updateNote(id: String, title: String, body: String, updatedAt: Long): Boolean {
        val metadataUpdated = updateObjectForNote(id, title, updatedAt)
        if (metadataUpdated == 0) return false
        check(updateNoteBody(id, body) == 1) { "Note content missing for workspace object $id" }
        deleteLegacyDraft(id)
        return true
    }

    @Query("UPDATE workspace_objects SET contentRef=:contentRef WHERE id=:id AND type='NOTE'")
    suspend fun setNoteContentRef(id: String, contentRef: String): Int

    @Query("UPDATE workspace_objects SET lifecycle = :lifecycle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setLifecycle(id: String, lifecycle: String, updatedAt: Long): Int

    @Query("UPDATE workspace_objects SET inInbox = :inInbox, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setInbox(id: String, inInbox: Boolean, updatedAt: Long): Int

    @Query("SELECT * FROM workspace_assets WHERE objectId = :id")
    suspend fun getAssetsForObject(id: String): List<WorkspaceAssetEntity>

    @Query("DELETE FROM workspace_assets WHERE objectId = :id")
    suspend fun deleteAssetsForObject(id: String): Int

    @Query("DELETE FROM workspace_objects WHERE id = :id AND type='NOTE' AND lifecycle = 'TRASHED'")
    suspend fun permanentlyDeleteTrashed(id: String): Int

}

data class WorkspaceSearchRow(
    val id: String,
    val type: String,
    val title: String,
    val snippet: String?
)

data class CanvasSearchRow(val canvasId: String, val nodeId: String, val boardTitle: String,
    val nodeType: String, val refObjectId: String?, val sourceType: String?, val sourceTitle: String?, val content: String?, val tags: String? = null)
