package com.halo.app.data.workspace

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.notebook.*
import com.halo.app.data.studio.StudioDocumentCodec
import org.json.JSONObject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

/** Read-only handoff metadata; content and edits remain with their existing canonical owners. */
enum class WorkspaceDestination { OPEN, FILE_TOOLS, STUDIO, STUDY, CHAT }
enum class WorkspaceSourceState { CURRENT, EARLIER_VERSION, UNAVAILABLE, VERSION_UNVERIFIED }
data class WorkspaceSourceLink(val objectId: String, val version: String, val title: String?, val state: WorkspaceSourceState, val depth: Int, val nodeId: String? = null)
data class WorkspaceCanvasDestination(val objectId: String, val title: String, val version: String)
data class WorkspaceContinuityItem(val objectId: String, val version: String, val title: String,
    val type: WorkspaceObjectType, val destinations: Set<WorkspaceDestination>, val sources: List<WorkspaceSourceLink>)

class WorkspaceContinuity(private val database: AppDatabase, private val context: WorkspaceContextStore,
    private val files: WorkspaceFileRepository, private val fileTools: WorkspaceFileTools,
    val requests: WorkspaceHandoffRequests) {
    val canvases = fileTools.workspace.observe(WorkspaceQuery(types = setOf(WorkspaceObjectType.CANVAS, WorkspaceObjectType.ARTIFACT))).map { rows ->
        rows.filter { it.type == WorkspaceObjectType.CANVAS || it.contentRef.startsWith("canvas:") }.take(100).mapNotNull { row ->
            objectVersion(row.id.value)?.let { WorkspaceCanvasDestination(row.id.value, row.title, it) }
        }
    }
    private suspend fun objectVersion(id: String): String? {
        val header = database.notebookDao().getWorkspaceObject(id)?.takeIf { it.lifecycle == "ACTIVE" } ?: return null
        return if (header.type == "CANVAS" || header.contentRef.startsWith("canvas:"))
            database.notebookCanvasDao().getDocument(id)?.revision?.let { "canvas:$it" }
        else context.versionFor(id)
    }
    suspend fun current(id: String, expectedVersion: String): WorkspaceContinuityItem = describe(id).also {
        check(it.version == expectedVersion) { "Source changed. Reopen it before continuing." }
    }
    suspend fun describe(id: String): WorkspaceContinuityItem = database.withTransaction {
        require(WorkspaceActionIds.isValid(id))
        val item = requireNotNull(database.notebookDao().getWorkspaceObject(id))
        require(item.lifecycle == "ACTIVE" && item.type in setOf("NOTE", "FILE", "MEDIA", "CANVAS", "ARTIFACT")) { "Source unavailable" }
        val version = requireNotNull(objectVersion(id)) { "Source unavailable" }
        val file = files.getFile(WorkspaceObjectId(id))
        val destinations = linkedSetOf(WorkspaceDestination.OPEN)
        val localFileTools = file?.let { WorkspaceTextFormat.fromFile(it.displayName, it.mimeType) != null || it.mimeType.startsWith("image/") || it.mimeType == "application/pdf" } == true
        if (item.type == "NOTE" || localFileTools) destinations += WorkspaceDestination.FILE_TOOLS
        if (item.contentRef.startsWith("studio:") || file?.mimeType?.startsWith("image/") == true) destinations += WorkspaceDestination.STUDIO
        val marker = if (item.type == "NOTE") database.notebookDocumentDao().getBlocks(id).singleOrNull()?.language else null
        val text = (item.type == "NOTE" && marker !in setOf(WorkspaceFileReceipts.LANGUAGE, WorkspaceFileAnnotations.LANGUAGE)) || file?.mimeType?.startsWith("text/") == true || file?.mimeType in setOf("application/json", com.halo.app.study.StudyTextExtractor.DOCX)
        val image = file?.mimeType in setOf("image/png", "image/jpeg", "image/webp") && file?.sha256 != null
        if (text || image || file?.mimeType == "application/pdf") destinations += WorkspaceDestination.STUDY
        if (text || image) destinations += WorkspaceDestination.CHAT
        WorkspaceContinuityItem(id, version, item.title, WorkspaceObjectType.valueOf(item.type), destinations, lineage(id))
    }
    suspend fun requestOpen(id: String, expectedVersion: String, destination: WorkspaceDestination): WorkspaceHandoffRequest {
        val item = current(id, expectedVersion)
        require(destination in item.destinations) { "This handoff is unavailable" }
        return requests.offer(id, expectedVersion, destination)
    }
    /** Canonical selection, never Memory promotion or an off-device transfer. */
    suspend fun selectWorkingSet(id: String, expectedVersion: String): Unit = database.withTransaction {
        current(id, expectedVersion)
        context.select(id)
    }
    suspend fun insert(canvasId: String, canvasVersion: String, id: String, expectedVersion: String): String = database.withTransaction {
        current(id, expectedVersion)
        current(canvasId, canvasVersion).also { require(it.type == WorkspaceObjectType.CANVAS || database.notebookDao().getWorkspaceObject(canvasId)?.contentRef?.startsWith("canvas:") == true) }
        val source = fileTools.get(id)
        fileTools.attachToCanvas(canvasId, id, source.version)
    }
    suspend fun route(id: String, expectedVersion: String, destination: WorkspaceDestination, handoffId: String? = null): String {
        if (handoffId != null) require(java.util.UUID.fromString(handoffId).toString() == handoffId)
        val item = current(id, expectedVersion)
        require(destination in item.destinations)
        val header = requireNotNull(database.notebookDao().getWorkspaceObject(id))
        val encoded = android.net.Uri.encode(id)
        return when (destination) {
            WorkspaceDestination.FILE_TOOLS -> "file_tools?objectId=$encoded"
            WorkspaceDestination.STUDY -> "study?captureSource=$encoded&sourceVersion=${android.net.Uri.encode(item.version)}"
            WorkspaceDestination.CHAT -> "home" // Host first selects the exact object through the existing interaction-context adapter.
            WorkspaceDestination.STUDIO -> if (header.contentRef.startsWith("studio:")) "studio?projectId=$encoded"
                else "studio?sourceId=$encoded&sourceVersion=${android.net.Uri.encode(item.version)}" + (handoffId?.let { "&handoffId=${android.net.Uri.encode(it)}" } ?: "")
            WorkspaceDestination.OPEN -> when {
                header.contentRef.startsWith("studio:") -> "studio?projectId=$encoded"
                header.type == "CANVAS" || header.contentRef.startsWith("canvas:") -> "notebook/canvas/$encoded"
                header.type == "NOTE" && header.contentRef == "file-tools:$id" -> "file_tools?objectId=$encoded"
                header.type == "NOTE" -> "notebook/note/$encoded"
                else -> "notebook/file/$encoded"
            }
        }
    }
    suspend fun handoffResult(handoffId: String): WorkspaceContinuityItem? {
        require(java.util.UUID.fromString(handoffId).toString() == handoffId)
        val origins = database.workspaceContextDao().handoffResults("%\"handoffId\":\"$handoffId\"%")
        if (origins.isEmpty()) return null
        val origin = origins.single()
        require(JSONObject(origin.metadata).getString("operation") == "studio-source-open")
        return describe(origin.objectId)
    }
    private suspend fun lineage(id: String): List<WorkspaceSourceLink> {
        data class Pending(val id: String, val version: String, val depth: Int, val attachmentId: String? = null, val nodeId: String? = null, val digest: String? = null)
        val queue = ArrayDeque<Pending>()
        val seen = hashSetOf<String>()
        val result = mutableListOf<WorkspaceSourceLink>()
        suspend fun enqueue(objectId: String, depth: Int, includeCurrentStudio: Boolean = true) {
            if (depth > 4) return
            val provenance = database.workspaceContextDao().provenanceFor(objectId)
            if (provenance != null) {
                val metadata = provenance.metadata.takeIf { it.length <= 4_000_000 }?.let { runCatching { JSONObject(it) }.getOrNull() }
                val attachmentId = metadata?.takeIf { it.optString("operation") == "studio-import-attachment" && it.optString("assetId") == provenance.revision }
                    ?.optString("attachmentId")?.takeIf { it.isNotBlank() }
                val localNode = metadata?.optString("sourceNodeId")?.takeIf { it == provenance.sourceId }
                val canvasId = metadata?.optString("canvasId")?.takeIf { WorkspaceActionIds.isValid(it) }
                if (localNode != null && canvasId != null) queue += Pending(canvasId, provenance.revision, depth,
                    nodeId = localNode, digest = metadata?.optString("sourceDigest")?.takeIf { it.matches(Regex("[a-f0-9]{64}")) })
                else queue += Pending(provenance.sourceId, provenance.revision, depth, attachmentId)
                if (metadata?.optString("operation") == "file-image-collage") metadata.optJSONArray("sources")?.let { sources ->
                    for (index in 0 until minOf(sources.length(), 6)) {
                        val source = sources.optJSONObject(index) ?: continue
                        queue += Pending(source.optString("id"), source.optString("version"), depth)
                    }
                }
                if (metadata?.optString("operation") in setOf("studio-export", "studio-ai-proposal-kept")) {
                    metadata?.optJSONObject("document")?.let { doc -> runCatching { StudioDocumentCodec.decode(doc.toString()) }.getOrNull() }
                        ?.layers?.mapNotNull { it.source }?.distinct()?.forEach { queue += Pending(it.objectId, it.version, depth) }
                }
            }
            if (includeCurrentStudio) database.studioProjectDao().get(objectId)?.let { project ->
                runCatching { com.halo.app.data.studio.StudioSession.decode(project.session) }.getOrNull()
                    ?.current?.layers?.mapNotNull { it.source }?.distinct()?.forEach { queue += Pending(it.objectId, it.version, depth) }
            }
        }
        enqueue(id, 1)
        while (queue.isNotEmpty() && result.size < 24) {
            val source = queue.removeFirst()
            if (!WorkspaceActionIds.isValid(source.id) || source.version.length !in 1..200 || source.id == id || !seen.add(source.id + ":" + source.version + ":" + source.nodeId.orEmpty())) continue
            val header = database.notebookDao().getWorkspaceObject(source.id)?.takeIf { it.lifecycle == "ACTIVE" && it.type != "MEMORY" }
            val node = if (header != null && source.nodeId != null) database.notebookCanvasDao().getNode(source.id, source.nodeId) else null
            val currentVersion = if (header == null) null else if (source.nodeId != null) node?.updatedAtEpochMs?.toString()
                else if (source.attachmentId != null) database.workspaceAssetDao().observeAttachments(source.id).first().singleOrNull { it.id == source.attachmentId }?.assetId
                else objectVersion(source.id)
            val state = when {
                currentVersion == null -> WorkspaceSourceState.UNAVAILABLE
                source.nodeId != null && source.digest == null -> WorkspaceSourceState.VERSION_UNVERIFIED
                source.nodeId != null -> if (java.security.MessageDigest.getInstance("SHA-256").digest(requireNotNull(node).text.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) } == source.digest) WorkspaceSourceState.CURRENT else WorkspaceSourceState.EARLIER_VERSION
                currentVersion == source.version -> WorkspaceSourceState.CURRENT
                else -> WorkspaceSourceState.EARLIER_VERSION
            }
            result += WorkspaceSourceLink(source.id, source.version, header?.title, state, source.depth, source.nodeId)
            if (header != null) enqueue(source.id, source.depth + 1, currentVersion == source.version)
        }
        return result
    }
}
