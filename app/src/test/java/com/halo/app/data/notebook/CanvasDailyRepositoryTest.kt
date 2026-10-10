package com.halo.app.data.notebook

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.search.WorkspaceLocalContentSearch
import com.halo.app.data.search.WorkspaceSearchQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CanvasDailyRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: NotebookCanvasRepository
    private lateinit var notes: RoomNotebookRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        repository = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao())
        notes = RoomNotebookRepository(db.notebookDao())
    }
    @After fun close() { db.close() }
    @Test fun capturedPhotoReusesInboxIdentityAndGetsImageCardSize() = runTest {
        val board = repository.create()
        val photo = notes.createNote("Photo caption")
        db.workspaceAssetDao().insertAsset(com.halo.app.data.workspace.WorkspaceAssetEntity(
            "photo-asset", photo.id, "IMAGE", "image/jpeg", "APP_OWNED", "content://capture/photo", true,
            "photo.jpg", 100, 800, 600, null, 1,
        ))
        repository.addReferences(board, listOf(photo.id))
        repository.addReferences(board, listOf(photo.id))
        val card = repository.observe(board).first()!!.nodes.single()
        assertEquals(photo.id, card.refObjectId)
        assertEquals(CanvasNodeType.IMAGE, card.type)
        assertEquals(280f, card.height)
        repository.deleteNode(board, card.id)
        assertNotNull(notes.getNote(photo.id))
        assertEquals(1, db.notebookDao().getAssetsForObject(photo.id).size)
    }
    @Test fun approvalRollsBackEarlierWritesWhenLaterGroupFails() = runTest {
        val board = repository.create()
        val first = repository.addTextNode(board, 0f, 0f, "first")
        val second = repository.addTextNode(board, 5f, 5f, "second")
        val before = repository.snapshot(board)
        val preview = proposal(board).copy(groups = listOf(CanvasOrganizationGroup("Allowed", listOf(first)), CanvasOrganizationGroup("Blocked", listOf(second))))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER deny_group BEFORE INSERT ON canvas_nodes WHEN NEW.type = 'GROUP' AND NEW.text = 'Blocked' BEGIN SELECT RAISE(ABORT, 'Rejected'); END")
        assertTrue(runCatching { repository.applyOrganization(preview) }.isFailure)
        assertEquals(before, repository.snapshot(board))
        assertTrue(notes.observeNotes().first().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER deny_group")
        assertNotNull(repository.applyOrganization(preview))
    }
    @Test fun groupMovementIsAtomicAndLifecycleFlushDoesNotResurrectRemovedCards() = runTest {
        val board = repository.create()
        val first = repository.addTextNode(board, 0f, 0f, "first")
        val second = repository.addTextNode(board, 5f, 5f, "second")
        val original = repository.snapshot(board)
        assertTrue(runCatching { repository.moveNodes(board, mapOf(first to (10f to 10f), "missing" to (20f to 20f))) }.isFailure)
        assertEquals(original, repository.snapshot(board))
        repository.moveNodes(board, mapOf(first to (10f to 10f), second to (20f to 20f)))
        repository.deleteNode(board, second)
        repository.saveEditorPending(board, mapOf(first to "latest", second to "late"), mapOf(second to (30f to 30f)), Triple(1f, 2f, 1.5f))
        val result = repository.observe(board).first()!!
        assertEquals(listOf("latest"), result.nodes.map { it.text })
        assertEquals(1.5f, result.viewportScale)
    }
    private suspend fun proposal(board: String): CanvasOrganizationProposal {
        val nodes = repository.observe(board).first()!!.nodes
        return CanvasOrganizationProposal(canvasId = board, sourceFingerprints = nodes.associate { it.id to canvasNodeFingerprint(it) },
            sourceObjectVersions = nodes.mapNotNull { it.refObjectId }.associateWith { repository.sourceObjectVersion(it)!! },
            groups = listOf(CanvasOrganizationGroup("Project", nodes.map { it.id })), nextSteps = listOf("Read the sources"))
    }
    @Test fun approvalIsAtomicIdempotentAndUndoPreservesSourceNote() = runTest {
        val board = repository.create("Board")
        val source = notes.createNote("Original facts")
        repository.addReference(board, source.id, 1f, 2f)
        repository.addTextNode(board, 10f, 10f, "Idea")
        val original = repository.observe(board).first()!!.nodes
        val preview = proposal(board)
        val undo = repository.applyOrganization(preview)!!
        assertEquals("Original facts", notes.getNote(source.id)!!.body)
        assertEquals(2, notes.observeNotes().first().size)
        val restarted = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao())
        assertNull(restarted.applyOrganization(preview))
        assertEquals(2, notes.observeNotes().first().size)
        restarted.undo(board, undo)
        assertEquals(original, restarted.observe(board).first()!!.nodes)
        assertNotNull(notes.getNote(source.id))
        assertEquals(2, notes.observeNotes().first().size)
    }
    @Test fun staleCardRejectsAllOperationsBeforeMutation() = runTest {
        val board = repository.create()
        val id = repository.addTextNode(board, 0f, 0f, "before")
        val preview = proposal(board)
        repository.updateText(board, id, "newer")
        assertTrue(runCatching { repository.applyOrganization(preview) }.isFailure)
        assertEquals(listOf("newer"), repository.observe(board).first()!!.nodes.map { it.text })
        assertTrue(notes.observeNotes().first().isEmpty())
    }
    @Test fun changedLinkedSourceRejectsProposalAndUndoRejectsLaterEdit() = runTest {
        val board = repository.create()
        val note = notes.createNote("version one")
        val card = repository.addReference(board, note.id, 0f, 0f)
        val stale = proposal(board)
        notes.saveNote(note.id, "version two")
        assertTrue(runCatching { repository.applyOrganization(stale) }.isFailure)
        val undo = repository.applyOrganization(proposal(board))!!
        repository.moveNode(board, card, 100f, 100f)
        assertTrue(runCatching { repository.undo(board, undo) }.isFailure)
        assertEquals("version two", notes.getNote(note.id)!!.body)
    }
    @Test fun reviewedTranscriptIsInboxNoteLinkedToOriginalAndSearchableInThreeLanguages() = runTest {
        val board = repository.create()
        val source = notes.createNote("Recording source")
        val card = repository.addReference(board, source.id, 0f, 0f)
        val original = repository.observe(board).first()!!.nodes.single().text
        val reviewed = "English answer · Русский ответ · Latviešu atbilde"
        val id = repository.saveReviewedText(board, card, original, reviewed, "Transcript", "review-1")
        assertEquals(id, repository.saveReviewedText(board, card, original, reviewed, "Transcript", "review-1"))
        assertEquals(2, notes.observeNotes().first().size)
        assertEquals("Recording source", notes.getNote(source.id)!!.body)
        assertNotNull(db.workspaceContextDao().provenanceMetadata("review-1:note"))
        for (term in listOf("ANSWER", "РУССКИЙ", "LATVIEŠU")) {
            val hits = WorkspaceLocalContentSearch.cards(db.notebookDao().canvasSearchCandidates(), WorkspaceSearchQuery(term, canvasId = board))
            assertEquals(id, hits.single().nodeId)
        }
        repository.deleteNode(board, id)
        assertNotNull(notes.getNote("review-1:note"))
        assertTrue(WorkspaceLocalContentSearch.cards(db.notebookDao().canvasSearchCandidates(), WorkspaceSearchQuery("РУССКИЙ", canvasId = board)).isEmpty())
    }
    @Test fun markupRefusesStaleOverwriteAndDrawingDraftSurvivesRepositoryRecreation() = runTest {
        val board = repository.create()
        val fileId = "file"
        db.notebookDao().insertWorkspaceObject(WorkspaceObjectEntity(fileId, "FILE", "PDF", 1, 1, "IMPORT", "ACTIVE", true, "READY", "file:$fileId"))
        val card = repository.addReference(board, fileId, 0f, 0f)
        val ink = CanvasSketchCodec.encode(listOf(listOf(CanvasSketchPoint(10, 20), CanvasSketchPoint(30, 40))))
        assertTrue(repository.saveFileMarkup(board, card, mapOf(1 to ink), "PDF"))
        assertTrue(runCatching { repository.saveFileMarkup(board, card, mapOf(2 to ink), "PDF") }.isFailure)
        assertEquals(setOf(1), CanvasFileMarkupCodec.decode(repository.observe(board).first()!!.nodes.single().text).pages.keys)
        val draft = CanvasDrawingDraft(card, "annotation", "PDF", pages = mapOf(2 to ink), page = 2)
        repository.saveDrawingDraft(board, draft)
        val restarted = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao())
        assertEquals(draft, restarted.drawingDraft(board))
        restarted.clearDrawingDraft(board)
        assertNull(restarted.drawingDraft(board))
    }
    @Test fun untrustedOrganizationCannotReferenceUnknownCardsOrExecuteActions() = runTest {
        val board = repository.create()
        val id = repository.addTextNode(board, 0f, 0f, "data")
        val base = proposal(board)
        assertTrue(runCatching { CanvasOrganizationCodec.parse("{\"groups\":[{\"title\":\"X\",\"memberIds\":[\"missing\"]}]}", base) }.isFailure)
        assertTrue(runCatching { CanvasOrganizationCodec.parse("{\"groups\":[{\"title\":\"X\",\"memberIds\":[\"$id\"]}],\"tool\":\"delete\"}", base) }.isFailure)
        assertEquals(listOf("data"), repository.observe(board).first()!!.nodes.map { it.text })
    }
    @Test fun clearAndUngroupNeverDeleteLinkedWorkspaceObjects() = runTest {
        val board = repository.create()
        val note = notes.createNote("Keep me")
        val card = repository.addReference(board, note.id, 0f, 0f)
        val undo = repository.manualEdit(board, setOf(card), "Clear")
        assertTrue(repository.observe(board).first()!!.nodes.isEmpty())
        assertNotNull(notes.getNote(note.id))
        repository.undo(board, undo)
        assertEquals(card, repository.observe(board).first()!!.nodes.single().id)
    }
}
