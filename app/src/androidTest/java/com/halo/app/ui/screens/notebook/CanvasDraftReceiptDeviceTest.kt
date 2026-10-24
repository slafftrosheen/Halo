package com.halo.app.ui.screens.notebook

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.CanvasDrawingDraft
import com.halo.app.data.notebook.NotebookCanvasRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** File-backed owner recreation on Android, not an OS process-death or physical-pen claim. */
@RunWith(AndroidJUnit4::class)
class CanvasDraftReceiptDeviceTest {
    @Test fun identicalReplacementKeepsItsIdentityAcrossDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "canvas-receipt-fixture-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var database = open()
        try {
            var repository = NotebookCanvasRepository(database, database.notebookCanvasDao(), database.notebookDao())
            val board = repository.create()
            val older = CanvasDrawingDraft("card", "sketch", "sketch:v1|100,100", sketch = "sketch:v1|200,200")
            val newer = CanvasDrawingDraft("card", "sketch", older.original, sketch = older.sketch)
            repository.saveDrawingDraft(board, older)
            repository.saveDrawingDraft(board, older.copy(sketch = "sketch:v1|300,300"))
            repository.saveDrawingDraft(board, newer)
            database.close()
            database = open()
            repository = NotebookCanvasRepository(database, database.notebookCanvasDao(), database.notebookDao())
            assertEquals(newer, repository.drawingDraft(board))
            assertFalse(repository.clearDrawingDraft(board, older))
            assertEquals(newer, repository.drawingDraft(board))
            assertTrue(repository.clearDrawingDraft(board, newer))
            assertNull(repository.drawingDraft(board))
            val legacy = CanvasDrawingDraft("file", "annotation", "original", pages = mapOf(1 to "sketch:v1|100,100"), receiptId = null)
            repository.saveDrawingDraft(board, legacy)
            assertEquals(legacy, repository.drawingDraft(board))
            val current = CanvasDrawingDraft("file", "annotation", "original", pages = legacy.pages)
            repository.saveDrawingDraft(board, current)
            assertFalse(repository.clearDrawingDraft(board, legacy))
            assertTrue(repository.clearDrawingDraft(board, current))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
