package com.halo.app.notebook

import com.halo.app.data.Memory
import com.halo.app.data.PersistedTurn
import com.halo.app.data.RoomConversationStore
import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.memory.MemoryRecord
import com.halo.app.data.memory.MemoryRepository
import com.halo.app.data.memory.MemoryCandidate
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.data.workspace.WorkspaceFileRepository
import android.net.Uri
import com.halo.app.data.search.WorkspaceSearchEngine
import com.halo.app.data.search.WorkspaceSearchQuery
import com.halo.app.data.search.WorkspaceSearchResult
import com.halo.app.data.workspace.WorkspaceQuery
import com.halo.app.data.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.Flow

/** Notebook-facing operations over the repositories also used by runtime tools. */
class WorkspaceUseCases(
    private val repository: WorkspaceRepository,
    private val searchEngine: WorkspaceSearchEngine,
    private val context: WorkspaceContextStore,
    private val conversations: RoomConversationStore,
    private val files: WorkspaceFileRepository,
    private val memoryRepository: MemoryRepository,
    private val notes: NotebookUseCases
) {
    val workingSet: Flow<List<String>> = context.workingSet
    val memories: Flow<List<Memory>> = context.memories
    val history: Flow<List<PersistedTurn>> = conversations.history
    val useMemories = context.useMemories
    val cloudSharing = context.cloudSharing

    fun observe(query: WorkspaceQuery): Flow<List<WorkspaceObject>> = repository.observe(query)
    suspend fun getObject(id: String): WorkspaceObject? = repository.get(WorkspaceObjectId(id))
    suspend fun contextText(id: String): String? = context.contentFor(id)
    suspend fun sourceVersion(id: String): String? = context.versionFor(id)
    suspend fun searchCanvas(id: String, text: String, sourceTypes: Set<com.halo.app.data.notebook.WorkspaceObjectType> = emptySet()): WorkspaceSearchResult =
        searchEngine.search(WorkspaceSearchQuery(text, maxHits = 100, canvasId = id, sourceTypes = sourceTypes))
    fun refreshContextPolicy() = context.refreshPolicy()
    fun remoteAllowed(type: String): Boolean = context.remoteAllowed(type)
    /**
     * Import an Android document into a stable, app-owned Workspace file before linking it
     * to a board. The 64 MiB limit prevents unexpected unbounded copies from providers.
     * If the copy fails the provisional Workspace row is moved to trash, never linked.
     */
    suspend fun importForCanvas(uri: Uri, displayName: String? = null, mimeType: String? = null,
        source: com.halo.app.data.notebook.WorkspaceObjectSource = com.halo.app.data.notebook.WorkspaceObjectSource.IMPORT): WorkspaceFile {
        val imported = files.importFile(uri, displayName = displayName, mimeType = mimeType, source = source)
        return try {
            files.keepLocalCopyBounded(imported.objectId, 64L * 1024L * 1024L)
        } catch (error: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { runCatching {
                // Cancellation can arrive after the owned copy was committed. Keep that durable Inbox item.
                if (files.getFile(imported.objectId)?.ownership != com.halo.app.data.workspace.ContentOwnership.HALO_OWNED) repository.trash(imported.objectId)
            } }
            throw error
        }
    }

    fun observeFile(id: String): Flow<WorkspaceFile?> = files.observeFile(WorkspaceObjectId(id))
    suspend fun getFile(id: String): WorkspaceFile? = files.getFile(WorkspaceObjectId(id))
    suspend fun keepLocalCopy(id: String): WorkspaceFile = files.keepLocalCopy(WorkspaceObjectId(id))
    suspend fun relinkFile(id: String, uri: Uri): WorkspaceFile = files.relink(WorkspaceObjectId(id), uri)
    suspend fun getMemory(id: String): MemoryRecord? = memoryRepository.getMemory(id)
    suspend fun correctMemory(id: String, text: String): Boolean = memoryRepository.correctMemory(id, text)
    suspend fun forgetMemory(id: String): Boolean = memoryRepository.forgetMemory(id)
    suspend fun search(text: String): WorkspaceSearchResult =
        searchEngine.search(WorkspaceSearchQuery(text, maxHits = 100))
    suspend fun select(id: String) = context.select(id)
    suspend fun remove(id: String) = context.remove(id)
    suspend fun remember(id: String) {
        val note = notes.getNote(id) ?: return
        if (note.body.isNotBlank()) {
            memoryRepository.promoteCandidate(MemoryCandidate(
                content = note.body,
                sourceObjectId = WorkspaceObjectId(id),
                sourceRevision = note.revision
            ))
        }
    }
    suspend fun forget(id: Int) = context.forget(id)
    suspend fun correct(memory: Memory, text: String) = context.correct(memory, text)
    suspend fun openConversation(id: String) = conversations.open(id)
    fun setUseMemories(value: Boolean) = context.setUseMemories(value)
    fun setCloudSharing(value: Boolean) = context.setCloudSharing(value)
    suspend fun archive(id: String) = repository.archive(WorkspaceObjectId(id))
    suspend fun trash(id: String) = repository.trash(WorkspaceObjectId(id))
    suspend fun restore(id: String) = repository.restore(WorkspaceObjectId(id))
    suspend fun removeFromInbox(id: String) = repository.removeFromInbox(WorkspaceObjectId(id))
}
