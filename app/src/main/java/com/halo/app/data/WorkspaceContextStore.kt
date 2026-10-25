package com.halo.app.data

import androidx.room.*
import com.halo.app.data.workspace.contentVersion
import com.halo.app.data.workspace.isReadable
import com.halo.app.ai.readRuntimeAiContextPolicy
import com.halo.app.ai.savedPrivacyMode
import com.halo.app.data.notebook.*
import com.halo.runtime.cognition.context.WorkspaceContextSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

@Entity(tableName = "working_set")
data class WorkingSetEntry(
    @PrimaryKey val objectId: String,
    val addedAt: Long = 0L,
    val source: String = "USER"
)

@Entity(tableName = "object_provenance")
data class ObjectProvenance(@PrimaryKey val objectId: String, val sourceId: String, val revision: String, val metadata: String)

@Dao
interface WorkspaceContextDao {
    @Query("SELECT objectId FROM working_set") fun observeWorkingSet(): Flow<List<String>>
    @Query("SELECT objectId FROM working_set ORDER BY addedAt DESC") suspend fun selectedIds(): List<String>
    @Query("SELECT * FROM working_set ORDER BY addedAt DESC") suspend fun selectedEntries(): List<WorkingSetEntry>
    @Query("SELECT * FROM working_set WHERE objectId = :id LIMIT 1") suspend fun selectedEntry(id: String): WorkingSetEntry?
    @Query("SELECT notes.body FROM notes INNER JOIN working_set ON notes.objectId = working_set.objectId INNER JOIN workspace_objects ON workspace_objects.id = notes.objectId WHERE workspace_objects.lifecycle = 'ACTIVE'")
    suspend fun selectedContent(): List<String>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(entry: WorkingSetEntry)
    @Query("DELETE FROM working_set WHERE objectId = :id") suspend fun remove(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun provenance(value: ObjectProvenance)

    @Query("SELECT objectId FROM object_provenance WHERE sourceId = :sourceId AND revision = :captureId LIMIT 1")
    suspend fun captureObject(sourceId: String, captureId: String): String?

    /** Raw provenance metadata for one object; used to restore a saved structured answer (P2). */
    @Query("SELECT metadata FROM object_provenance WHERE objectId = :id LIMIT 1")
    suspend fun provenanceMetadata(id: String): String?
    @Query("SELECT * FROM object_provenance WHERE objectId = :id LIMIT 1")
    suspend fun provenanceFor(id: String): ObjectProvenance?
    @Query("SELECT * FROM object_provenance WHERE metadata LIKE :marker LIMIT 2")
    suspend fun handoffResults(marker: String): List<ObjectProvenance>

}

/** Explicit promotion and selection. Notes enter context only through these actions. */
class WorkspaceContextStore(private val database: AppDatabase, private val notes: NotebookRepository, private val storage: com.halo.sdk.runtime.Storage) : WorkspaceContextSource {
    override val resolvedTextRequiresVersion = true
    override suspend fun referenceMetadata(objectId: String): com.halo.runtime.cognition.context.WorkspaceReferenceMetadata? = database.withTransaction {
        val version = versionFor(objectId) ?: return@withTransaction null
        val item = database.notebookDao().getWorkspaceObject(objectId) ?: return@withTransaction null
        com.halo.runtime.cognition.context.WorkspaceReferenceMetadata(objectId, version, item.title.take(200))
    }
    val useMemories = kotlinx.coroutines.flow.MutableStateFlow(storage.readRuntimeAiContextPolicy().includeMemories)
    val cloudSharing = kotlinx.coroutines.flow.MutableStateFlow(storage.readRuntimeAiContextPolicy().allowCloudWorkspace)
    fun setUseMemories(enabled: Boolean) { if (storage.savedPrivacyMode() == null) storage.putBoolean("policy_memories", enabled); refreshPolicy() }
    fun setCloudSharing(enabled: Boolean) { if (storage.savedPrivacyMode() == null) storage.putBoolean("policy_cloud_workspace", enabled); refreshPolicy() }
    fun refreshPolicy() {
        cloudSharing.value = storage.readRuntimeAiContextPolicy().allowCloudWorkspace
        useMemories.value = storage.readRuntimeAiContextPolicy().includeMemories
    }
    fun remoteAllowed(type: String): Boolean = storage.readRuntimeAiContextPolicy().let {
        it.allowCloudWorkspace && (type != "MEMORY" || it.allowCloudMemories) }
    val workingSet = database.workspaceContextDao().observeWorkingSet()
    val memories = database.memoryRecordDao().observeActiveRecords().map { rows -> rows.filter { it.id.startsWith("mem_") }.map { row ->
        Memory(row.id.removePrefix("mem_").toIntOrNull() ?: 0,row.content,row.updatedAt,row.sourceObjectId,row.sourceRevision)
    } }
    suspend fun select(id: String): Unit = database.withTransaction {
        val item = requireNotNull(database.notebookDao().getWorkspaceObject(id))
        require(item.lifecycle == "ACTIVE") { "Source is not active" }
        database.workspaceFileDao().getFile(id)?.let { require(it.isReadable()) { "Source needs relinking" } }
        database.workspaceContextDao().add(WorkingSetEntry(id, System.currentTimeMillis()))
        Unit
    }
    suspend fun selectInkSelection(canvasId: String, selectionNodeId: String,
        expected: com.halo.sdk.runtime.ink.InkSelectionContext? = null): Unit = database.withTransaction {
        require(selectionReferenceText(canvasId, selectionNodeId, expected) != null) { "Selection is stale or unavailable." }
        database.workspaceContextDao().add(WorkingSetEntry(canvasId, System.currentTimeMillis(),
            "$INK_SELECTION_SOURCE_PREFIX$selectionNodeId"))
    }
    /** Rendering cannot substitute a different bookmark or restore a deselected referent. */
    suspend fun requireCurrentInkSelection(canvasId: String, selectionNodeId: String,
        expected: com.halo.sdk.runtime.ink.InkSelectionContext) =
        withCurrentInkSelection(canvasId, selectionNodeId, expected) {}

    /** The synchronous context commit shares the canonical source/selection read transaction. */
    suspend fun withCurrentInkSelection(canvasId: String, selectionNodeId: String,
        expected: com.halo.sdk.runtime.ink.InkSelectionContext, commit: () -> Unit): Unit = database.withTransaction {
        require(database.workspaceContextDao().selectedEntry(canvasId)?.source ==
            "$INK_SELECTION_SOURCE_PREFIX$selectionNodeId" &&
            selectionReferenceText(canvasId, selectionNodeId, expected) != null) {
            "Source changed. Select the region again."
        }
        commit()
    }
    suspend fun remove(id: String) = database.workspaceContextDao().remove(id)
    suspend fun remember(id: String): Unit = database.withTransaction {
        require(database.notebookDao().getWorkspaceObject(id)?.lifecycle == "ACTIVE") { "Source is not active" }
        val marker = database.notebookDocumentDao().getBlocks(id).singleOrNull()?.language
        require(marker !in setOf(com.halo.app.data.workspace.WorkspaceFileReceipts.LANGUAGE, com.halo.app.data.workspace.WorkspaceFileAnnotations.LANGUAGE)) { "Review an explicit text fact before adding it to Memory" }
        val note = requireNotNull(notes.getNote(id))
        com.halo.app.data.memory.RoomMemoryRepository(database).promoteCandidate(com.halo.app.data.memory.MemoryCandidate(note.body,WorkspaceObjectId(id),note.revision))
        Unit
    }
    suspend fun correct(memory: Memory, text: String) {
        require(text.isNotBlank())
        com.halo.app.data.memory.RoomMemoryRepository(database).correctMemory("mem_${memory.id}",text)
    }
    suspend fun forget(id: Int) { com.halo.app.data.memory.RoomMemoryRepository(database).forgetMemory("mem_$id") }
    override suspend fun content(includeMemories: Boolean): List<String> = entries(includeMemories).map { it.text }
    override suspend fun entries(includeMemories: Boolean): List<com.halo.runtime.cognition.context.WorkspaceContextEntry> =
        database.withTransaction {
            database.workspaceContextDao().selectedEntries().take(20).mapNotNull { entry ->
                resolveWorkingSetEntry(entry)?.let { (type, text) ->
                    if (type == "MEMORY" && !includeMemories) null else {
                        val version = versionFor(entry.objectId) ?: return@mapNotNull null
                        com.halo.runtime.cognition.context.WorkspaceContextEntry(entry.objectId,
                            "Working set ${type.lowercase()}: ${text.take(8_000)}", version)
                    }
                }
            }
        }

    override suspend fun contentFor(objectId: String): String? =
        resolveContent(objectId)?.let { (type, text) ->
            "Explicit ${type.lowercase()} reference: ${text.take(8_000)}"
        }

    /** The revision and content share the existing Workspace/Room read boundary. */
    override suspend fun snapshot(objectId: String, expectedVersion: String?): com.halo.runtime.cognition.context.WorkspaceContextEntry? =
        database.withTransaction {
            val version = versionFor(objectId) ?: return@withTransaction null
            if (expectedVersion != null && expectedVersion != version) return@withTransaction null
            val text = contentFor(objectId) ?: return@withTransaction null
            if (versionFor(objectId) != version) return@withTransaction null
            com.halo.runtime.cognition.context.WorkspaceContextEntry(objectId, text, version)
        }

    override suspend fun versionFor(objectId: String): String? {
        val item = database.notebookDao().getWorkspaceObject(objectId)?.takeIf { it.lifecycle == "ACTIVE" } ?: return null
        return when(item.type) {
            "NOTE" -> notes.getNote(objectId)?.revision?.toString()?.let { revision ->
                val attachments = database.workspaceAssetDao().observeAttachments(objectId).first()
                if(attachments.isEmpty()) revision else {
                    val identity = attachments.joinToString("|") { "${it.id}:${it.assetId}:${it.type}:${it.mimeType}:${it.bytes}" }
                    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
                    "$revision:$hash"
                }
            }
            "FILE", "MEDIA", "ARTIFACT" -> database.workspaceFileDao().getFile(objectId)?.takeIf { it.isReadable() }?.contentVersion()
                ?: when {
                    item.contentRef.startsWith("studio:") -> database.studioProjectDao().get(objectId)?.revision?.toString()
                    item.contentRef.startsWith("canvas:") -> database.notebookCanvasDao().getDocument(objectId)?.revision?.let { "canvas:$it" }
                    else -> null
                }
            "CANVAS" -> {
                val selectionEntry = database.workspaceContextDao().selectedEntry(objectId)
                    ?.takeIf { it.source.startsWith(INK_SELECTION_SOURCE_PREFIX) }
                if (selectionEntry == null) database.notebookCanvasDao().getDocument(objectId)?.revision?.let { "canvas:$it" } else {
                    val selectionId = selectionEntry.source.removePrefix(INK_SELECTION_SOURCE_PREFIX).takeIf { it.isNotBlank() }
                        ?: return null
                    val reference = selectionReferenceText(objectId, selectionId) ?: return null
                    "ink-selection:" + java.security.MessageDigest.getInstance("SHA-256")
                        .digest(reference.toByteArray(Charsets.UTF_8))
                        .joinToString("") { byte -> "%02x".format(byte) }
                }
            }
            "MEMORY" -> if (resolveContent(objectId) != null) item.updatedAt.toString() else null
            else -> item.updatedAt.toString()
        }
    }

    override suspend fun isMemory(objectId: String): Boolean = database.notebookDao().getWorkspaceObject(objectId)?.type == "MEMORY"

    private suspend fun resolveWorkingSetEntry(entry: WorkingSetEntry): Pair<String, String>? {
        if (entry.source.startsWith(INK_SELECTION_SOURCE_PREFIX)) {
            val selectionId = entry.source.removePrefix(INK_SELECTION_SOURCE_PREFIX).takeIf { it.isNotBlank() } ?: return null
            val item = database.notebookDao().getWorkspaceObject(entry.objectId)
                ?.takeIf { it.lifecycle == "ACTIVE" && it.type == WorkspaceObjectType.CANVAS.name } ?: return null
            return selectionReferenceText(entry.objectId, selectionId)?.let { item.type to it }
        }
        return resolveContent(entry.objectId)
    }

    private suspend fun selectionReferenceText(canvasId: String, selectionNodeId: String,
        expected: com.halo.sdk.runtime.ink.InkSelectionContext? = null): String? {
        val item = database.notebookDao().getWorkspaceObject(canvasId)
            ?.takeIf { it.lifecycle == "ACTIVE" && it.type == WorkspaceObjectType.CANVAS.name } ?: return null
        val nodes = database.notebookCanvasDao().getNodes(canvasId)
        val selection = nodes.firstOrNull { it.id == selectionNodeId && it.type == CanvasNodeType.INK_SELECTION.name } ?: return null
        val context = InkSelectionBookmarkCodec.decode(selection.text) ?: return null
        if (expected != null && context != expected) return null
        val sourceId = database.notebookCanvasDao().getEdges(canvasId)
            .firstOrNull { it.toNodeId == selectionNodeId && it.label == "selection" }?.fromNodeId ?: return null
        val source = nodes.firstOrNull { it.id == sourceId } ?: return null
        if (source.refObjectId != null && database.notebookDao().getWorkspaceObject(source.refObjectId)
                ?.lifecycle != WorkspaceObjectLifecycle.ACTIVE.name) return null
        val type = runCatching { CanvasNodeType.valueOf(source.type) }.getOrNull() ?: return null
        val sourceVersion = source.refObjectId?.let { id ->
            database.workspaceFileDao().getFile(id)?.takeIf { it.isReadable() }?.contentVersion()
        }
        if (InkSelectionBookmarkCodec.resolve(context, canvasId, type, source.id, source.refObjectId,
                source.text, sourceVersion) == null) return null
        return "Canvas '${item.title}': ${InkSelectionBookmarkCodec.referenceText(context)}"
    }

    private suspend fun resolveContent(id: String): Pair<String, String>? {
        val item = database.notebookDao().getWorkspaceObject(id) ?: return null
        if (item.lifecycle != "ACTIVE") return null
        val text = when (item.type) {
            "NOTE" -> {
                val blocks = database.notebookDocumentDao().getBlocks(id)
                val literal = blocks.singleOrNull()?.takeIf { it.type == "CODE" &&
                    com.halo.app.data.workspace.WorkspaceTextFormat.fromLanguage(it.language) != null }
                when (blocks.singleOrNull()?.language) {
                    com.halo.app.data.workspace.WorkspaceFileReceipts.LANGUAGE -> "Local batch receipt. Open File tools to review its saved outputs."
                    com.halo.app.data.workspace.WorkspaceFileAnnotations.LANGUAGE -> "PDF annotation note. Open its linked PDF or explicitly export a page image to use its content."
                    else -> literal?.text ?: notes.getNote(id)?.body
                }
            }
            "MEMORY" -> database.memoryRecordDao().getRecord(id)?.takeIf { it.status == "ACTIVE" && (it.validUntil == null || it.validUntil > System.currentTimeMillis()) &&
                (it.scope == "GLOBAL" || it.scopeId in database.workspaceContextDao().selectedIds().take(20)) &&
                com.halo.runtime.cognition.memory.MemorySensitivityClassifier.classify(it.content) != com.halo.runtime.cognition.memory.MemorySensitivity.SECRET }?.content
            "FILE", "MEDIA" -> database.workspaceFileDao().getFile(id)?.takeIf { it.isReadable() }?.let {
                "${it.displayName} (${it.mimeType})"
            }
            "CANVAS", "ARTIFACT" -> if (item.contentRef.startsWith("canvas:")) {
                database.notebookCanvasDao().getNodes(id).joinToString("\n\n") { node ->
                    when {
                        node.type == CanvasNodeType.SKETCH.name -> "[hand-drawn sketch]"
                        node.type == CanvasNodeType.INK_SELECTION.name -> "[saved ink selection]"
                        node.refObjectId == null -> node.text
                        else -> "Linked workspace item: ${CanvasFileMarkupCodec.displayTitle(node.text)}. Select that source separately to include its content."
                    }
                }.take(8000).ifBlank { item.title }
            } else item.title
            "CONVERSATION" -> database.conversationDao().turns(id).takeLast(3)
                .joinToString("\n") { "User: ${it.userText}\nHalo: ${it.assistantText}" }
            else -> item.title
        }?.takeIf { it.isNotBlank() } ?: return null
        return item.type to text
    }

    private companion object {
        const val INK_SELECTION_SOURCE_PREFIX = "INK_SELECTION:"
    }
}
