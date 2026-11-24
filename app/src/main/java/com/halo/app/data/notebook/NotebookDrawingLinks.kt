package com.halo.app.data.notebook

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.workspace.privateLibraryClaim
import com.halo.app.ui.canvas.v13.InkSheets
import com.halo.app.ui.notes.v13.NoteDrawing
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Notes store a normal Workspace reference plus the exact sketch node to open. */
class NotebookDrawingLinks(
    private val database: AppDatabase,
    private val documents: NotebookDocumentRepository,
    private val canvases: NotebookCanvasRepository,
) {
    internal suspend fun create(noteId: String, revision: Long, after: String?, label: String,
        sheets: List<String>, verify: () -> Unit): NotebookDocument = database.withTransaction {
        verify()
        checkNote(noteId, revision, after)
        val board = canvases.create(label.take(120), inInbox = false)
        val first = if (sheets.size == 1) canvases.addSketchNode(board, 80f, 80f, sheets.single())
        else canvases.saveDrawingSheets(board, null, null, 80f, 80f, sheets).first
        commitLink(noteId, revision, link(after, board, first, label), verify)
    }

    internal suspend fun insert(noteId: String, revision: Long, after: String?, canvasId: String, nodeId: String,
        label: String, verify: () -> Unit): NotebookDocument = database.withTransaction {
        verify()
        checkNote(noteId, revision, after)
        commitLink(noteId, revision, link(after, canvasId, nodeId, label), verify)
    }

    private suspend fun checkNote(noteId: String, revision: Long, after: String?) {
        val note = requireNotNull(documents.load(noteId))
        check(note.isWritable && note.revision == revision && note.blocks.size < DocumentReducer.MAX_BLOCKS)
        check(after == null || note.blocks.any { it.id == after })
        check(database.privateLibraryDao().claims(listOf(privateLibraryClaim(noteId))).isEmpty())
    }

    private suspend fun commitLink(noteId: String, revision: Long, command: DocumentCommand.InsertReference,
        verify: () -> Unit): NotebookDocument {
        verify()
        check(documents.apply(noteId, revision, command) is CommitOutcome.Committed)
        verify()
        return requireNotNull(documents.load(noteId))
    }

    internal suspend fun link(after: String?, canvasId: String, nodeId: String, label: String): DocumentCommand.InsertReference {
        require(canvases.currentVersion(canvasId) != null)
        require(database.privateLibraryDao().claims(listOf(privateLibraryClaim(canvasId))).isEmpty())
        require(canvases.snapshot(canvasId).first.any { it.id == nodeId && it.type == CanvasNodeType.SKETCH.name })
        return DocumentCommand.InsertReference(after, canvasId, label, NoteDrawing.ref(canvasId, nodeId))
    }

    private fun observeBoard(canvasId: String) = combine(
        canvases.observe(canvasId), database.privateLibraryDao().observeClaim(privateLibraryClaim(canvasId)),
    ) { board, claimed -> board.takeUnless { claimed } }

    internal fun observeSketches(canvasId: String) = observeBoard(canvasId).map { board ->
        board?.nodes.orEmpty().filter { it.type == CanvasNodeType.SKETCH }
    }

    internal fun observe(canvasId: String, nodeId: String) = observeBoard(canvasId).map { board ->
        if (board == null) return@map emptyList()
        val byId = board.nodes.filter { it.type == CanvasNodeType.SKETCH }.associateBy { it.id }
        val seen = mutableSetOf<String>()
        val ink = mutableListOf<String>()
        var next: String? = nodeId
        while (next != null && ink.size < InkSheets.MAX_SHEETS) {
            val id = next
            val node = byId[id] ?: break
            if (!seen.add(id)) break
            ink += node.text
            next = board.edges.firstOrNull { it.fromNodeId == id && it.label == InkSheets.CONTINUES_LABEL }?.toNodeId
        }
        ink.toList()
    }
}
