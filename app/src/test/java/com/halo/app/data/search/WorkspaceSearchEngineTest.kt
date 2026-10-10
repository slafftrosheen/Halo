package com.halo.app.data.search

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.notebook.RoomNotebookRepository
import com.halo.app.data.notebook.NotebookCanvasRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkspaceSearchEngineTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var searchEngine: AppSearchWorkspaceSearchEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        searchEngine = AppSearchWorkspaceSearchEngine(context, db.notebookDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun indexAndSearchWorkspaceObject() = runTest {
        val id = WorkspaceObjectId("note-123")
        searchEngine.indexObject(
            objectId = id,
            type = WorkspaceObjectType.NOTE,
            title = "Halo UI Redesign",
            content = "Detailed specifications for paper-like soft pastel interface",
            updatedAt = System.currentTimeMillis()
        )

        val result = searchEngine.search(WorkspaceSearchQuery("Halo"))
        assertNotNull(result)
        assertEquals("Halo", result.query)
    }

    @Test fun searchesLongMultilingualContentBeyondFirstPageAndExcludesCredentialsAnywhere() = runTest {
        val notes = RoomNotebookRepository(db.notebookDao())
        val target = notes.createNote("plain ".repeat(2500) + "Русский ответ Latviešu atbilde", title = "Older note")
        repeat(70) { notes.createNote("ordinary text", title = "Recent $it") }
        assertTrue(searchEngine.search(WorkspaceSearchQuery("РУССКИЙ")).hits.any { it.objectId.value == target.id })
        assertTrue(searchEngine.search(WorkspaceSearchQuery("LATVIEŠU")).hits.any { it.objectId.value == target.id })
        val secret = notes.createNote("Latviešu safe phrase " + "plain ".repeat(2500) + "password: super-secret-credential", title = "Sensitive")
        assertTrue(searchEngine.search(WorkspaceSearchQuery("Latviešu")).hits.none { it.objectId.value == secret.id })
    }
    @Test fun staleDerivedIndexCannotResurrectEditedOrRemovedBoardCard() = runTest {
        val boards = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao())
        val board = boards.create("Board")
        val card = boards.addTextNode(board, 0f, 0f, "Original needle")
        searchEngine.indexObject(WorkspaceObjectId(board), WorkspaceObjectType.CANVAS, "Board", "Original needle", 1)
        val hit = searchEngine.search(WorkspaceSearchQuery("needle", canvasId = board)).hits.single()
        assertEquals(card, hit.nodeId)
        boards.updateText(board, card, "New content")
        assertTrue(searchEngine.search(WorkspaceSearchQuery("needle", canvasId = board)).hits.isEmpty())
        val other = boards.addTextNode(board, 1f, 1f, "Current needle")
        val currentHits = searchEngine.search(WorkspaceSearchQuery("needle", canvasId = board)).hits
        assertEquals(listOf(other), currentHits.map { it.nodeId })
        assertTrue(currentHits.none { it.snippet?.contains("Original needle") == true })
        boards.deleteNode(board, other)
        assertEquals(card, searchEngine.search(WorkspaceSearchQuery("New content", canvasId = board)).hits.single().nodeId)
        boards.deleteNode(board, card)
        assertTrue(searchEngine.search(WorkspaceSearchQuery("New content", canvasId = board)).hits.isEmpty())
    }
}
