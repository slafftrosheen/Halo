package com.halo.app.context

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.ObjectProvenance
import com.halo.app.data.notebook.NotebookRepository
import com.halo.app.data.notebook.WorkspaceSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ContextResultSaveReview(val conversationId: String,val turnId: String,val text: String)

/** Reviewed exact result; retries share existing Workspace provenance identity. */
class ContextResultSaveOwner(private val database: AppDatabase,private val notes: NotebookRepository) {
    private val lock = Mutex()
    suspend fun review(conversationId: String,turnId: String): ContextResultSaveReview? {
        val turn = database.conversationDao().turnById(turnId)?.takeIf { it.conversationId == conversationId && it.assistantText.isNotBlank() } ?: return null
        return ContextResultSaveReview(conversationId,turnId,turn.assistantText)
    }
    suspend fun confirm(review: ContextResultSaveReview): String = lock.withLock {
        database.withTransaction {
            val turn = database.conversationDao().turnById(review.turnId)
            check(turn?.conversationId == review.conversationId && turn.assistantText == review.text) { "Result unavailable or changed" }
            val sourceId = "context-result:${review.conversationId}"
            database.workspaceContextDao().captureObject(sourceId,review.turnId)?.let { return@withTransaction it }
            val note = notes.createNote(review.text,WorkspaceSource.IMPORT)
            database.workspaceContextDao().provenance(ObjectProvenance(note.id,sourceId,review.turnId,
                org.json.JSONObject(turn.metadata).put("conversationId",review.conversationId).put("turnId",review.turnId)
                    .put("claimStatus","MODEL_GENERATED_UNVERIFIED").toString()))
            note.id
        }
    }
}
