package com.halo.app.ui.screens.notebook

import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.notebook.WorkspaceObjectSource
import com.halo.app.data.notebook.WorkspaceObjectType
import org.junit.Assert.*
import org.junit.Test

class NotebookLocalSearchSourcesTest {
    private fun item(id: String,type: WorkspaceObjectType,
        lifecycle: WorkspaceObjectLifecycle = WorkspaceObjectLifecycle.ACTIVE) =
        NotebookWorkspaceItem(WorkspaceObject(WorkspaceObjectId(id), type, "Source $id",
            1L,2L,WorkspaceObjectSource.USER_CREATED,lifecycle,false,""))

    @Test fun localSearchOnlyOffersActiveTextBackedNotebookRecords() {
        val available = notebookSearchSources(listOf(
            item("n1",WorkspaceObjectType.NOTE),
            item("board1",WorkspaceObjectType.CANVAS),
            item("image1",WorkspaceObjectType.MEDIA),
            item("chat1",WorkspaceObjectType.CONVERSATION),
            item("trashed",WorkspaceObjectType.NOTE,WorkspaceObjectLifecycle.TRASHED),
        ), emptyList())
        assertEquals(listOf("note:n1","canvas:board1"),available.map { it.id })
    }

    @Test fun duplicateItemsDoNotExpandSourceAllowlist() {
        val one = item("n1",WorkspaceObjectType.NOTE)
        val ids = notebookSearchSources(listOf(one,one), emptyList()).map { it.id }
        assertEquals(listOf("note:n1"),ids)
    }

    @Test fun lowMemoryAndUnknownFailuresUseBoundedUserLanguage() {
        assertTrue(localSearchUserError(IllegalStateException("MEMORY_NOT_ADMITTED")).contains("memory"))
        val raw = IllegalArgumentException("private/source/path/export.json")
        val message = localSearchUserError(raw)
        assertFalse(message.contains("private/source"))
        assertFalse(message.contains("export.json"))
    }
}
