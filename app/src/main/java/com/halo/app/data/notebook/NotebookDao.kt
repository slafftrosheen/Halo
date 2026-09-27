package com.halo.app.data.notebook

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.halo.app.data.workspace.WorkspaceAssetEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

@Dao
interface NotebookDao {
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

    @Query("UPDATE notes SET body = :body, revision = revision + 1 WHERE objectId = :id")
    suspend fun updateNoteBody(id: String, body: String): Int

    @Query("DELETE FROM note_drafts WHERE objectId = :id")
    suspend fun deleteDraft(id: String): Int

    @Transaction
    suspend fun updateNote(id: String, title: String, body: String, updatedAt: Long): Boolean {
        val metadataUpdated = updateObjectForNote(id, title, updatedAt)
        if (metadataUpdated == 0) return false
        check(updateNoteBody(id, body) == 1) { "Note content missing for workspace object $id" }
        deleteDraft(id)
        return true
    }

    @Query("UPDATE workspace_objects SET lifecycle = :lifecycle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setLifecycle(id: String, lifecycle: String, updatedAt: Long): Int

    @Query("UPDATE workspace_objects SET inInbox = :inInbox, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setInbox(id: String, inInbox: Boolean, updatedAt: Long): Int

    @Query("SELECT * FROM workspace_assets WHERE objectId = :id")
    suspend fun getAssetsForObject(id: String): List<WorkspaceAssetEntity>

    @Query("DELETE FROM workspace_assets WHERE objectId = :id")
    suspend fun deleteAssetsForObject(id: String): Int

    @Query("DELETE FROM workspace_objects WHERE id = :id AND lifecycle = 'TRASHED'")
    suspend fun permanentlyDeleteTrashed(id: String): Int

    @Transaction
    suspend fun purgePermanently(id: String): Boolean {
        val assets = getAssetsForObject(id)
        val rows = permanentlyDeleteTrashed(id)
        if (rows > 0) {
            deleteAssetsForObject(id)
            assets.filter { it.storageType == "MANAGED_FILE" }.forEach { asset ->
                File(asset.pathOrUri).takeIf { it.exists() }?.delete()
            }
        }
        return rows > 0
    }
}

data class WorkspaceSearchRow(
    val id: String,
    val type: String,
    val title: String,
    val snippet: String?
)
