package com.halo.app.data.workspace

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.*
import kotlinx.coroutines.flow.first

enum class WorkspaceFileToolsKind { FILE, TEXT_DOCUMENT, PDF_ANNOTATIONS, BATCH_RECEIPT, NOTE }
data class WorkspaceFileToolsObject(val id: String, val title: String, val version: String, val kind: WorkspaceFileToolsKind,
    val mimeType: String? = null)

/** Coordinates existing document/file/Canvas owners; contains no second storage or execution engine. */
data class WorkspaceFileToolsSnapshot(val selected: WorkspaceFileToolsObject, val file: WorkspaceFile?,
    val text: WorkspaceTextDocument?, val annotations: WorkspacePdfAnnotations?, val receipt: WorkspaceBatchReceipt?, val recovery: RecoveryDraft?)

class WorkspaceFileTools(private val database: AppDatabase, val text: WorkspaceTextDocuments,
    val media: WorkspaceMediaFiles, val annotations: WorkspaceFileAnnotations, val receipts: WorkspaceFileReceipts,
    val files: WorkspaceFileRepository, val workspace: WorkspaceRepository, val versions: WorkspaceFileVersions,
    private val documents: NotebookDocumentRepository, private val canvases: NotebookCanvasRepository,
    val requests: WorkspaceFileToolsRequests) {
    suspend fun get(id: String): WorkspaceFileToolsObject {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        val item = requireNotNull(workspace.get(WorkspaceObjectId(id)))
        require(item.lifecycle == WorkspaceObjectLifecycle.ACTIVE)
        if (item.type == WorkspaceObjectType.FILE) {
            val file = requireNotNull(files.getFile(item.id))
            return WorkspaceFileToolsObject(id, item.title, requireNotNull(file.version), WorkspaceFileToolsKind.FILE, file.mimeType)
        }
        require(item.type == WorkspaceObjectType.NOTE)
        val note = requireNotNull(documents.load(id))
        val language = note.blocks.singleOrNull()?.takeIf { it.type == NoteBlockType.CODE }?.language
        val kind = when {
            WorkspaceTextFormat.fromLanguage(language) != null -> { requireNotNull(text.get(id)); WorkspaceFileToolsKind.TEXT_DOCUMENT }
            language == WorkspaceFileAnnotations.LANGUAGE -> { requireNotNull(annotations.get(id)); WorkspaceFileToolsKind.PDF_ANNOTATIONS }
            language == WorkspaceFileReceipts.LANGUAGE -> { receipts.get(id); WorkspaceFileToolsKind.BATCH_RECEIPT }
            else -> WorkspaceFileToolsKind.NOTE
        }
        return WorkspaceFileToolsObject(id, item.title, note.revision.toString(), kind)
    }
    suspend fun snapshot(id: String): WorkspaceFileToolsSnapshot = database.withTransaction {
        val selected = get(id)
        val textDocument = if (selected.kind == WorkspaceFileToolsKind.TEXT_DOCUMENT) requireNotNull(text.get(id)) else null
        val marks = if (selected.kind == WorkspaceFileToolsKind.PDF_ANNOTATIONS) requireNotNull(annotations.get(id)) else null
        val file = if (selected.kind == WorkspaceFileToolsKind.FILE) files.getFile(WorkspaceObjectId(id))
            else marks?.let { runCatching { versions.current(it.source) }.getOrNull() }
        WorkspaceFileToolsSnapshot(selected, file, textDocument, marks,
            if (selected.kind == WorkspaceFileToolsKind.BATCH_RECEIPT) receipts.get(id) else null,
            if (marks != null) annotations.recovery(id) else null)
    }
    /** Explicit local-edit preparation, separate from read-only metadata and assistant presentation dispatch. */
    suspend fun prepareForEditing(id: String) {
        val selected = get(id)
        if (selected.kind == WorkspaceFileToolsKind.FILE &&
            (selected.mimeType?.startsWith("image/") == true || selected.mimeType == "application/pdf")) {
            val file = requireNotNull(files.getFile(WorkspaceObjectId(id)))
            if (file.availability in setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL))
                versions.pin(WorkspaceFileVersion(id, selected.version))
        }
    }
    suspend fun requestOpen(id: String?): WorkspaceFileToolsRequest {
        if (id != null) get(id)
        return requests.offer(id)
    }
    suspend fun attachToCanvas(canvasId: String, sourceId: String, expectedVersion: String): String = database.withTransaction {
        val source = get(sourceId)
        require(source.version == expectedVersion)
        val canvas = requireNotNull(canvases.observe(canvasId).first())
        val x = canvas.nodes.maxOfOrNull { it.x.toDouble() + it.width }?.plus(24.0)?.coerceIn(-100_000.0, 100_000.0)?.toFloat() ?: 0f
        canvases.addReference(canvasId, sourceId, x, 0f, image = source.mimeType?.startsWith("image/") == true)
    }
}
