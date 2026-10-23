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
        val old = CanvasDrawingDraft("file", "annotation", "original", pages = mapOf(1 to "sketch:v1|10,20"))
        val decoded = requireNotNull(CanvasDrawingDraft.decode(old.encode()))
        assertEquals(old, decoded)
        assertTrue(decoded.matchesSource("file", "annotation", "original"))
        assertFalse(decoded.matchesSource("file", "annotation", "original", "verified-current-version"))
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
