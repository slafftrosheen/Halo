package com.halo.app.ui.screens.notebook

import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.notebook.WorkspaceObjectSource
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.workspace.NoteAttachment
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

    @Test fun pictureChoicesAreExactAttachmentsOwnedByVisibleActiveNotes() {
        val picture = NoteAttachment("a1","n1","asset1",0,"Diagram","IMAGE","image/png",
            "MANAGED_FILE","/private/image.png",true,"image.png",24L,10,10,null)
        val hidden = picture.copy(id="a2",noteId="trashed")
        val choices = notebookSearchSources(listOf(
            item("n1",WorkspaceObjectType.NOTE),
            item("trashed",WorkspaceObjectType.NOTE,WorkspaceObjectLifecycle.TRASHED),
        ), emptyList(), listOf(picture,hidden))
        assertEquals(listOf("note:n1","attachment:n1:a1"),choices.map { it.id })
        assertTrue(choices.last().title.contains("Diagram"))
    }

    @Test fun lowMemoryAndUnknownFailuresUseBoundedUserLanguage() {
        assertTrue(localSearchUserErrorMessage(IllegalStateException("MEMORY_NOT_ADMITTED")).contains("memory"))
        val raw = IllegalArgumentException("private/source/path/export.json")
        val message = localSearchUserErrorMessage(raw)
        assertFalse(message.contains("private/source"))
        assertFalse(message.contains("export.json"))
    }
}
