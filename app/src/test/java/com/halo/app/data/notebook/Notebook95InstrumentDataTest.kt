package com.halo.app.data.notebook

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class Notebook95InstrumentDataTest {
    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() { db.close() }

    @Test fun savedAndRecoveredNotesKeepLayoutFontsAndAnchoredDiscussion() = runTest {
        val docs = NotebookDocumentRepository(db.notebookDocumentDao(), db)
        val notes = RoomNotebookRepository(db.notebookDao(), documents = docs)
        val note = notes.createNote("Short note")
        docs.ensureDocument(note.id, note.body)
        val document = requireNotNull(docs.load(note.id))
        val block = document.blocks.single()
        val selection = DocumentSelection(block.id, 0, 5)
        val formatted = DocumentReducer.apply(document.blocks, DocumentCommand.SetFont(selection, "Serif", 22)) { "font" }.blocks
        val commented = DocumentReducer.apply(formatted, DocumentCommand.AddComment(selection, "AB", "Check this")) { "comment" }.blocks
        val replied = DocumentReducer.apply(commented,
            DocumentCommand.UpdateComment(block.id, "comment", "CD", "Kept", true)) { "reply" }.blocks
        val settings = NotePageSettings(size = NotePageSize.A5, landscape = true, columns = 2, header = "Chapter",
            footer = "Draft", pageNumbers = NotePageNumbers.FOOTER_RIGHT, firstPage = 5, trackChanges = true)
        docs.saveRecovery(document.noteId, replied, document.revision, 1, pageSettings = settings)
        val draft = requireNotNull(docs.loadRecovery(document.noteId))
        assertEquals(replied, draft.blocks)
        assertEquals(settings, draft.pageSettings)
        assertTrue(docs.commit(document.noteId, document.revision, replied, acknowledgeDraftSequence = 1, pageSettings = settings)
            is CommitOutcome.Committed)
        val saved = requireNotNull(docs.load(document.noteId))
        assertEquals(settings, saved.pageSettings)
        assertEquals(replied, saved.blocks)
        assertNull(docs.loadRecovery(document.noteId))
    }

    @Test fun separateTrackedChangesCanBeAcceptedAndRejectedIndependently() {
        val original = NoteBlock("a", 0, NoteBlockType.PARAGRAPH, "The red fox moves past the ancient tree.")
        val edited = original.withEditedText("The blue fox moves past the quiet tree.")
        val marked = trackNoteChanges(listOf(original), listOf(edited)).single()
        assertEquals(2, noteTextHunks(original.text, edited.text).size)
        val accepted = requireNotNull(resolveNoteChange(marked, true, 0))
        val rejected = requireNotNull(resolveNoteChange(accepted, false, 0))
        assertEquals("The blue fox moves past the ancient tree.", rejected.text)
        assertNull(rejected.presentation.change)
        val deleted = trackNoteChanges(listOf(original), emptyList()).single()
        assertTrue(deleted.isPendingDeletion())
        assertEquals("", NotebookDocumentCodec.plainText(listOf(deleted)))
        assertEquals(original, resolveNoteChange(deleted, false, null))
    }

    @Test fun groupedAndLockedCardsKeepTheirPositionsAndCommentPinsStayAttachedToBoardCoordinates() = runTest {
        val canvases = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao())
        val board = canvases.create()
        val first = canvases.addTextNode(board, 80f, 120f)
        val second = canvases.addTextNode(board, 420f, 240f)
        canvases.manualEdit(board, setOf(first, second), "Group selection")
        val initial = requireNotNull(canvases.observe(board).first())
        val group = initial.nodes.single { it.type == CanvasNodeType.GROUP }
        assertEquals(setOf(first, second), initial.edges.filter { it.fromNodeId == group.id }.map { it.toNodeId }.toSet())
        canvases.manualEdit(board, setOf(group.id), "Lock")
        val locked = requireNotNull(canvases.observe(board).first())
        assertEquals(setOf(group.id, first, second), canvasLockedIds(locked.nodes, locked.edges))
        assertTrue(runCatching { canvases.moveNode(board, first, 9f, 9f) }.isFailure)
        canvases.manualEdit(board, setOf(group.id), "Unlock")
        assertTrue(canvases.moveNode(board, group.id, group.x + 20f, group.y + 30f))
        val pin = canvases.addReviewPin(board, 40f, 60f, CanvasReviewPin("AB", "Review this"))
        val moved = requireNotNull(canvases.observe(board).first())
        assertEquals(100f, moved.nodes.single { it.id == first }.x, .01f)
        assertEquals(270f, moved.nodes.single { it.id == second }.y, .01f)
        val current = moved.nodes.single { it.id == pin }
        assertEquals(40f, current.x, .01f)
        assertEquals(60f, current.y, .01f)
        assertTrue(canvases.updateReviewPin(board, pin, current.text, CanvasReviewPin("AB", "Review this", resolved = true)))
        val saved = requireNotNull(canvases.observe(board).first()).nodes.single { it.id == pin }
        assertTrue(requireNotNull(CanvasReviewPin.decode(saved.text)).resolved)
    }
}
