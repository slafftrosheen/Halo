package com.halo.app.ui.workspace

import androidx.room.withTransaction
import com.halo.app.data.notebook.NotebookNote
import com.halo.app.data.workspace.WorkspaceDestination
import com.halo.app.di.ManualDependencyGraph as Graph

internal enum class WorkspaceNoteDestination(val label: String) {
    NOTES("Open in Notes"),
    CANVAS("Open on a new canvas"),
    STUDY("Open in Study"),
}

internal suspend fun prepareWorkspaceNoteHandoff(
    destination: WorkspaceNoteDestination,
    verify: () -> Unit,
    save: suspend () -> NotebookNote,
): String = Graph.appDatabase.withTransaction {
    verify()
    val note = save()
    val workspace = Graph.workspaceContinuity
    val item = workspace.describe(note.id)
    val route = when (destination) {
        WorkspaceNoteDestination.NOTES -> workspace.route(note.id, item.version, WorkspaceDestination.OPEN)
        WorkspaceNoteDestination.STUDY -> workspace.route(note.id, item.version, WorkspaceDestination.STUDY)
        WorkspaceNoteDestination.CANVAS -> {
            val board = Graph.notebookCanvasRepository.create(note.title, inInbox = false)
            val node = workspace.insert(board, workspace.describe(board).version, item.objectId, item.version)
            "notebook/canvas/${android.net.Uri.encode(board)}?nodeId=${android.net.Uri.encode(node)}"
        }
    }
    verify()
    route
}
