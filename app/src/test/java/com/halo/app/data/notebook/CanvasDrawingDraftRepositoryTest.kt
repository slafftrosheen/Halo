package com.halo.app.data.notebook

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CanvasDrawingDraftRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: NotebookCanvasRepository
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = NotebookCanvasRepository(database, database.notebookCanvasDao(), database.notebookDao())
    }
    @After fun close() = database.close()

    @Test fun recoveryCannotBorrowANewerSourceOrAnotherEditorIdentity() {
        val sketch = CanvasDrawingDraft("sketch-card", "sketch", "original ink", sketch = "recovered ink")
        assertTrue(sketch.matchesSource("sketch-card", "sketch", "original ink"))
        assertFalse(sketch.matchesSource("other-card", "sketch", "original ink"))
        assertFalse(sketch.matchesSource(null, "sketch", "original ink"))
        assertFalse(sketch.matchesSource("sketch-card", "annotation", "original ink"))
        assertFalse(sketch.matchesSource("sketch-card", "sketch", "new ink"))
        val annotation = CanvasDrawingDraft("file-card", "annotation", "original markup", fileVersion = "file-v1")
        assertTrue(annotation.matchesSource("file-card", "annotation", "original markup", "file-v1"))
        assertFalse(annotation.matchesSource("file-card", "annotation", "original markup", "file-v2"))
        assertFalse(annotation.matchesSource("file-card", "annotation", "new markup", "file-v1"))
        assertFalse(annotation.matchesSource("file-card", "annotation", "original markup"))
    }

    @Test fun legacyUnknownFileVersionCannotAcquireACurrentVersionOnRecovery() {
        val old = CanvasDrawingDraft("file", "annotation", "original", pages = mapOf(1 to "sketch:v1|10,20"), receiptId = null)
        val decoded = requireNotNull(CanvasDrawingDraft.decode(old.encode()))
        assertEquals(old, decoded)
        assertNull(decoded.receiptId)
        assertEquals(decoded, CanvasDrawingDraft.decode(old.encode()))
        assertTrue(decoded.matchesSource("file", "annotation", "original"))
        assertFalse(decoded.matchesSource("file", "annotation", "original", "verified-current-version"))
    }

    @Test fun currentReceiptRoundTripsAndCannotBeMissingMalformedOrUnknownVersion() {
        val draft = CanvasDrawingDraft("card", "sketch", "base", sketch = "ink")
        val json = org.json.JSONObject(draft.encode())
        assertEquals("canvas-drawing:v2", json.getString("format"))
        assertEquals(draft, CanvasDrawingDraft.decode(json.toString()))
        assertEquals(draft, CanvasDrawingDraft.decode(json.toString()))
        json.remove("receiptId")
        assertNull(CanvasDrawingDraft.decode(json.toString()))
        json.put("receiptId", "not-a-receipt")
        assertNull(CanvasDrawingDraft.decode(json.toString()))
        json.put("receiptId", draft.receiptId).put("format", "canvas-drawing:v99")
        assertNull(CanvasDrawingDraft.decode(json.toString()))
    }

    @Test fun identicalContentFromANewEditorCannotBeClearedByAnOldReceipt() = runBlocking {
        val board = repository.create()
        val older = CanvasDrawingDraft("card", "sketch", "original", sketch = "same ink")
        val newer = CanvasDrawingDraft("card", "sketch", "original", sketch = "same ink")
        assertNotEquals(older.receiptId, newer.receiptId)
        repository.saveDrawingDraft(board, older)
        repository.saveDrawingDraft(board, older.copy(sketch = "intermediate ink"))
        repository.saveDrawingDraft(board, newer)
        assertFalse(repository.clearDrawingDraft(board, older))
        assertEquals(newer, repository.drawingDraft(board))
        assertTrue(repository.clearDrawingDraft(board, newer))
    }

    @Test fun legacyReceiptCannotClearIdenticalContentFromANewEditor() = runBlocking {
        val board = repository.create()
        val legacy = CanvasDrawingDraft("card", "annotation", "original", pages = mapOf(1 to "ink"), receiptId = null)
        repository.saveDrawingDraft(board, legacy)
        val newer = CanvasDrawingDraft("card", "annotation", "original", pages = legacy.pages)
        repository.saveDrawingDraft(board, newer)
        assertFalse(repository.clearDrawingDraft(board, legacy))
        assertEquals(newer, repository.drawingDraft(board))
        assertTrue(repository.clearDrawingDraft(board, newer))
    }

    @Test fun receiptSurvivesDbReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Robolectric includes the method name in its Windows temp path. Keep both
        // names short so native SQLite can open the file without long-path support.
        val name = "dr-${java.util.UUID.randomUUID().toString().take(8)}.db"
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        var disk = open()
        try {
            val firstOwner = NotebookCanvasRepository(disk, disk.notebookCanvasDao(), disk.notebookDao())
            val board = firstOwner.create()
            val older = CanvasDrawingDraft("card", "sketch", "original", sketch = "same ink")
            val newer = CanvasDrawingDraft("card", "sketch", "original", sketch = "same ink")
            firstOwner.saveDrawingDraft(board, newer)
            disk.close()
            disk = open()
            val restoredOwner = NotebookCanvasRepository(disk, disk.notebookCanvasDao(), disk.notebookDao())
            assertEquals(newer, restoredOwner.drawingDraft(board))
            assertFalse(restoredOwner.clearDrawingDraft(board, older))
            assertTrue(restoredOwner.clearDrawingDraft(board, newer))
            assertNull(restoredOwner.drawingDraft(board))
        } finally {
            disk.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun completedEditCannotClearAReplacementDraftWithTheSameSource() = runBlocking {
        val board = repository.create()
        val first = CanvasDrawingDraft("card", "sketch", "original", sketch = "first draft")
        val replacement = first.copy(sketch = "newer draft")
        repository.saveDrawingDraft(board, first)
        repository.saveDrawingDraft(board, replacement)
        assertFalse(repository.clearDrawingDraft(board, first))
        assertEquals(replacement, repository.drawingDraft(board))
        assertTrue(repository.clearDrawingDraft(board, replacement))
        assertNull(repository.drawingDraft(board))
        assertFalse(repository.clearDrawingDraft(board, replacement))
    }
}
