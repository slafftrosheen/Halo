package com.halo.app.ui.screens.notebook

import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.notebook.WorkspaceObjectSource
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.notebook.CanvasImageSearchSource
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

    @Test fun audioChoicesAreExactAttachmentsOwnedByVisibleActiveNotes() {
        val memo = NoteAttachment("audio1","n1","asset-audio",0,"Lecture","FILE","audio/mp4",
            "MANAGED_FILE","/private/memo.m4a",true,"memo.m4a",4_000_000L,null,null,30_000L)
        val hidden = memo.copy(id="audio2",noteId="trashed")
        val notAudio = memo.copy(id="video1",mimeType="video/mp4")
        val choices = notebookSearchSources(listOf(
            item("n1",WorkspaceObjectType.NOTE),
            item("trashed",WorkspaceObjectType.NOTE,WorkspaceObjectLifecycle.TRASHED),
        ), emptyList(), audioAttachments=listOf(memo,hidden,notAudio))
        assertEquals(listOf("note:n1","attachment:n1:audio1"),choices.map { it.id })
        assertTrue(choices.last().title.contains("Lecture"))
    }

    @Test fun videoChoicesAreExactAttachmentsOwnedByVisibleActiveNotes() {
        val video = NoteAttachment("video1","n1","asset-video",0,"Lecture clip","FILE","video/mp4",
            "MANAGED_FILE","/private/clip.mp4",true,"clip.mp4",8_000_000L,null,null,null)
        val hidden = video.copy(id="video2",noteId="trashed")
        val notVideo = video.copy(id="other",mimeType="application/octet-stream")
        val choices = notebookSearchSources(listOf(
            item("n1",WorkspaceObjectType.NOTE),
            item("trashed",WorkspaceObjectType.NOTE,WorkspaceObjectLifecycle.TRASHED),
        ), emptyList(), videoAttachments=listOf(video,hidden,notVideo))
        assertEquals(listOf("note:n1","attachment:n1:video1"),choices.map { it.id })
        assertTrue(choices.last().title.contains("Lecture clip"))
    }

    @Test fun canvasPictureChoicesAreExactImagesOnVisibleActiveBoards() {
        val image = CanvasImageSearchSource("board1", "node1", "file1", "Ignored title", "diagram.png", "image/png")
        val hiddenBoard = image.copy(canvasId = "trashed")
        val unsupported = image.copy(nodeId = "node2", mimeType = "image/gif")
        val choices = notebookSearchSources(listOf(
            item("board1", WorkspaceObjectType.CANVAS),
            item("trashed", WorkspaceObjectType.CANVAS, WorkspaceObjectLifecycle.TRASHED),
        ), emptyList(), canvasImages = listOf(image, hiddenBoard, unsupported))

        assertEquals(listOf("canvas:board1", "canvas-image:board1:node1"), choices.map { it.id })
        assertTrue(choices.last().title.contains("diagram.png"))
    }

    @Test fun canvasImagesRemainAvailableWhenNotebookExceedsSourceLimit() {
        val manyNotes = (1..80).map { item("note$it", WorkspaceObjectType.NOTE) }
        val image = CanvasImageSearchSource("board1", "node1", "file1", "Board", "diagram.png", "image/png")
        val choices = notebookSearchSources(manyNotes + item("board1", WorkspaceObjectType.CANVAS), emptyList(),
            canvasImages = listOf(image))

        assertEquals(64, choices.size)
        assertTrue(choices.any { it.id == "canvas-image:board1:node1" })
    }

    @Test fun lowMemoryAndUnknownFailuresUseBoundedUserLanguage() {
        assertTrue(localSearchUserErrorMessage(IllegalStateException("MEMORY_NOT_ADMITTED")).contains("memory"))
        assertTrue(localSearchUserErrorMessage(IllegalStateException("AUDIO_SOURCE_COUNT_LIMIT")).contains("eight"))
        val raw = IllegalArgumentException("private/source/path/export.json")
        val message = localSearchUserErrorMessage(raw)
        assertFalse(message.contains("private/source"))
        assertFalse(message.contains("export.json"))
    }
}
