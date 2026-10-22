package com.halo.app.ui.screens.notebook

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.CanvasNodeType
import com.halo.app.data.notebook.NotebookCanvasRepository
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.notebook.WorkspaceObjectSource
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.workspace.WorkspaceFileEntity
import com.halo.sdk.runtime.ink.InkCodec
import com.halo.sdk.runtime.ink.InkDocument
import com.halo.sdk.runtime.ink.InkPoint
import com.halo.sdk.runtime.ink.InkSelectionBounds
import com.halo.sdk.runtime.ink.InkSelectionOrigin
import com.halo.sdk.runtime.ink.InkSelections
import com.halo.sdk.runtime.ink.InkStroke
import com.halo.sdk.runtime.ink.InkTool
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CanvasInkSelectionBookmarkTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: NotebookCanvasRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = NotebookCanvasRepository(database, database.notebookCanvasDao(), database.notebookDao())
    }

    @After fun tearDown() = database.close()

    @Test fun savedSketchBookmarkResolvesUntilItsSourceChanges() = runBlocking {
        val canvasId = repository.create()
        val original = ink("stroke-original")
        val sketchId = repository.addSketchNode(canvasId, 0f, 0f, InkCodec.encode(original))
        val selection = selection(original, canvasId, sketchId, "")

        val bookmarkId = repository.addInkSelection(canvasId, selection)
        val canvas = repository.observe(canvasId).first()!!
        val bookmark = canvas.nodes.single { it.id == bookmarkId }
        val source = canvas.nodes.single { it.id == sketchId }
        assertEquals(CanvasNodeType.INK_SELECTION, bookmark.type)
        assertTrue(bookmark.inkSelectionIsCurrent(source, canvasId))
        assertEquals("selection", canvas.edges.single { it.toNodeId == bookmarkId }.label)

        val changed = ink("stroke-replacement")
        assertTrue(repository.updateText(canvasId, sketchId, InkCodec.encode(changed)))
        val changedCanvas = repository.observe(canvasId).first()!!
        assertFalse(changedCanvas.nodes.single { it.id == bookmarkId }
            .inkSelectionIsCurrent(changedCanvas.nodes.single { it.id == sketchId }, canvasId))
    }

    @Test fun pdfSelectionCommitsMarkupAndBookmarkTogetherAndTracksFileVersion() = runBlocking {
        val canvasId = repository.create()
        val fileId = "file-selection-source"
        val timestamp = 1_000L
        database.notebookDao().insertObject(WorkspaceObjectEntity(
            id = fileId, type = WorkspaceObjectType.FILE.name, title = "worksheet.pdf",
            createdAt = timestamp, updatedAt = timestamp, source = WorkspaceObjectSource.IMPORT.name,
            lifecycle = WorkspaceObjectLifecycle.ACTIVE.name, inInbox = false, indexingState = "READY",
            contentRef = "file:$fileId"))
        database.workspaceFileDao().insertFile(WorkspaceFileEntity(
            objectId = fileId, uri = "content://test/worksheet.pdf", displayName = "worksheet.pdf",
            mimeType = "application/pdf", sizeBytes = 10, modifiedAt = timestamp, ownedCopyPath = null,
            permissionPersisted = true, sha256 = "file-v1"))
        val sourceNodeId = repository.addReference(canvasId, fileId, 0f, 0f)
        val document = ink("pdf-stroke")
        val encoded = InkCodec.encode(document)
        val selection = selection(document, fileId, "page-2", "file-v1")

        assertTrue(runCatching {
            repository.saveFileMarkup(canvasId, sourceNodeId, mapOf(2 to encoded),
                expectedText = "worksheet.pdf", expectedFileVersion = "file-v1",
                selection = selection.copy(sourceRevision = "stale-revision"))
        }.isFailure)
        val rejectedCanvas = repository.observe(canvasId).first()!!
        assertEquals("worksheet.pdf", rejectedCanvas.nodes.single { it.id == sourceNodeId }.text)
        assertFalse(rejectedCanvas.nodes.any { it.type == CanvasNodeType.INK_SELECTION })

        assertTrue(repository.saveFileMarkup(canvasId, sourceNodeId, mapOf(2 to encoded),
            expectedText = "worksheet.pdf", expectedFileVersion = "file-v1", selection = selection))

        val canvas = repository.observe(canvasId).first()!!
        val savedSource = canvas.nodes.single { it.id == sourceNodeId }
        val bookmark = canvas.nodes.single { it.type == CanvasNodeType.INK_SELECTION }
        assertTrue(savedSource.text.contains(encoded))
        assertTrue(bookmark.inkSelectionIsCurrent(savedSource, canvasId, "file-v1"))
        assertFalse(bookmark.inkSelectionIsCurrent(savedSource, canvasId, "file-v2"))
        assertEquals("selection", canvas.edges.single { it.toNodeId == bookmark.id }.label)
        assertNotNull(database.workspaceFileDao().getFile(fileId))
    }

    private fun ink(id: String) = InkDocument(listOf(InkStroke(InkTool.STYLUS,
        listOf(InkPoint(100, 500), InkPoint(700, 500)), id)))

    private fun selection(document: InkDocument, owner: String, page: String?, revisionBase: String) =
        requireNotNull(InkSelections.create(document, owner, page, sourceRevision(document, revisionBase),
            InkSelectionBounds(400, 400, 600, 600), InkSelectionOrigin.BOX, 2_000L))

    private fun sourceRevision(document: InkDocument, base: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((base + "\n" + InkCodec.encode(document)).toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
