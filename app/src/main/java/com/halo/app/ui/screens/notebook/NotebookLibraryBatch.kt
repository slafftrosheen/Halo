package com.halo.app.ui.screens.notebook

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.RoomConversationStore
import com.halo.app.data.notebook.*
import com.halo.app.notebook.WorkspaceUseCases
import kotlinx.coroutines.flow.first
import org.json.JSONArray

/** Internal canonical identities, never document content in a tool receipt. */
data class NotebookBatchSource internal constructor(
    val objectId: String,
    val version: String,
    val lifecycle: WorkspaceObjectLifecycle,
    internal val chatVersion: String? = null,
)

data class NotebookBatchAdmission internal constructor(
    val action: NotebookBatchAction,
    val sources: List<NotebookBatchSource>,
    val collectionId: String? = null,
    internal val collectionVersion: String? = null,
    val undo: Boolean = false,
    internal val memberships: Map<String, Boolean> = emptyMap(),
    val unavailableIds: Set<String> = emptySet(),
)

data class NotebookBatchReceipt(
    val action: NotebookBatchAction,
    val succeeded: Set<String>,
    val failed: Set<String>,
    val unknown: Set<String>,
    val undoAvailable: Boolean,
)

internal class NotebookBatchRefused : IllegalStateException("Library source or admission changed")

/** A Room boundary around the existing source, lifecycle, context and membership owners.
 * Saved chats enter their writer mutex BEFORE Room; no transaction ever waits on that mutex.
 */
class NotebookLibraryBatchOwner(
    private val database: AppDatabase,
    private val workspace: WorkspaceUseCases,
    private val organization: NotebookOrganizationRepository,
    private val conversations: RoomConversationStore,
) {
    suspend fun capture(id: String): NotebookBatchSource? {
        val row = database.notebookDao().getWorkspaceObject(id) ?: return null
        return if (row.type == "CONVERSATION") {
            conversations.withLibrarySavedChat(id, row.lifecycle) { version -> source(id, version) }
        } else database.withTransaction { source(id) }
    }

    suspend fun collectionVersion(id: String): String? = database.withTransaction {
        organization.captureGuarded(null, id).collections.singleOrNull { it.id == id }
            ?.let { NotebookNoteIdentity.digest(it.toString()) }
    }

    suspend fun membership(collectionId: String, objectId: String): Boolean = database.withTransaction {
        organization.captureGuarded(objectId, collectionId).members.any { it.objectId == objectId }
    }

    private suspend fun source(id: String, chatVersion: String? = null): NotebookBatchSource? {
        val row = database.notebookDao().getWorkspaceObject(id) ?: return null
        val content: Any = when {
            row.type == "CONVERSATION" -> chatVersion ?: return null
            row.type == "NOTE" -> listOf(database.notebookDocumentDao().getDocument(id) ?: return null,
                database.notebookDocumentDao().getBlocks(id),
                database.workspaceAssetDao().observeAttachments(id).first().sortedBy { it.id },
                database.notebookDao().getAssetsForObject(id).sortedBy { it.id })
            row.type == "CANVAS" || row.contentRef.startsWith("canvas:") ->
                database.notebookCanvasDao().getDocument(id) ?: return null
            row.contentRef.startsWith("studio:") -> database.studioProjectDao().get(id) ?: return null
            row.type == "MEMORY" -> database.memoryRecordDao().getRecord(id) ?: return null
            row.type in setOf("FILE", "MEDIA", "ARTIFACT") -> database.workspaceFileDao().getFile(id) ?: return null
            else -> return null
        }
        // Full canonical rows and content revisions, including lifecycle; no wall-clock-only identity.
        val version = "library-source:" + NotebookNoteIdentity.digest(JSONArray().put(row.toString()).put(content.toString()).toString())
        return NotebookBatchSource(id, version, WorkspaceObjectLifecycle.valueOf(row.lifecycle), chatVersion)
    }

    /** Failure rolls back this object only. Earlier object receipts remain true. */
    suspend fun apply(expected: NotebookBatchSource, admission: NotebookBatchAdmission,
        admit: suspend () -> Boolean): NotebookBatchSource {
        suspend fun commit(chatVersion: String? = null): NotebookBatchSource {
            fun requireAdmission(value: Boolean) { if (!value) throw NotebookBatchRefused() }
            requireAdmission(admit())
            requireAdmission(source(expected.objectId, chatVersion) == expected)
            val id = expected.objectId
            if (database.notebookDao().getWorkspaceObject(id)?.type == "NOTE" &&
                admission.action in setOf(NotebookBatchAction.ARCHIVE, NotebookBatchAction.TRASH, NotebookBatchAction.RESTORE))
                requireAdmission(database.notebookDao().getDraft(id) == null)
            when (admission.action) {
                NotebookBatchAction.CONTEXT -> {
                    requireAdmission(expected.lifecycle == WorkspaceObjectLifecycle.ACTIVE)
                    workspace.select(id) // Existing explicit working set and budget/sharing policy.
                }
                NotebookBatchAction.COLLECTION -> {
                    requireAdmission(expected.lifecycle != WorkspaceObjectLifecycle.TRASHED)
                    val collectionId = requireNotNull(admission.collectionId)
                    requireAdmission(collectionVersion(collectionId) == admission.collectionVersion)
                    requireAdmission(membership(collectionId, id) == admission.memberships[id])
                    check(organization.setMembership(collectionId, id, true))
                }
                NotebookBatchAction.ARCHIVE -> {
                    requireAdmission(expected.lifecycle == WorkspaceObjectLifecycle.ACTIVE)
                    workspace.archive(id)
                }
                NotebookBatchAction.TRASH -> {
                    requireAdmission(expected.lifecycle != WorkspaceObjectLifecycle.TRASHED)
                    workspace.trash(id)
                }
                NotebookBatchAction.RESTORE -> {
                    requireAdmission(expected.lifecycle != WorkspaceObjectLifecycle.ACTIVE)
                    workspace.restore(id)
                }
            }
            requireAdmission(admit())
            // Kept in the same transaction. This captures the exact successful Trash version for Undo.
            val after = requireNotNull(source(id, chatVersion))
            requireAdmission(admit())
            return after
        }
        return if (expected.chatVersion != null) conversations.withLibrarySavedChat(
            expected.objectId, expected.lifecycle.name, expected.chatVersion,
            changesLifecycle = admission.action in setOf(NotebookBatchAction.ARCHIVE, NotebookBatchAction.TRASH, NotebookBatchAction.RESTORE),
        ) { version ->
            commit(version)
            // Hash again after the lifecycle change while the conversation writer remains locked.
            val after = requireNotNull(source(expected.objectId, conversations.librarySavedChatVersionLocked(expected.objectId)))
            if (!admit()) throw NotebookBatchRefused()
            after
        } else database.withTransaction { commit() }
    }
}
