package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.halo.app.context.CanvasWorkflowConflict
import com.halo.app.notebook.NotebookUseCases
import com.halo.app.data.workspace.WorkspaceFileToolsFixture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CanvasWorkflowPersistenceTest {
    private lateinit var f: WorkspaceFileToolsFixture
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(Dispatchers.Unconfined); f = WorkspaceFileToolsFixture() }
    @After fun close() { store.clear(); f.close(); Dispatchers.resetMain() }
    private suspend fun editor(board: String, allowed: Boolean = true): CanvasEditorViewModel {
        val vm = CanvasEditorViewModel(board, f.canvases, notes = NotebookUseCases(f.notes), permitsAi = { _, _ -> allowed })
        store.put("canvas", vm)
        withTimeout(5000) { vm.uiState.first { !it.loading } }
        return vm
    }
    private suspend fun conflict(block: suspend () -> Unit) {
        try { block(); fail("Expected exact source/draft conflict") } catch (_: CanvasWorkflowConflict) { }
    }
    @Test fun exportReturnsOnlyPersistedNoteAndUndispatchedStudyTarget() = runBlocking {
        val board = f.canvases.create("Ideas")
        f.canvases.addTextNode(board, 0f, 0f, "Original board idea")
        val vm = editor(board)
        val before = vm.workflowStatus()
        val result = vm.workflowExport(before.version, true) {}
        assertEquals("SAVED", result.phase)
        val saved = requireNotNull(f.notes.getNote(requireNotNull(result.noteId)))
        assertTrue(saved.body.contains("Original board idea"))
        assertTrue(result.navigationTarget!!.startsWith("study?captureSource=${saved.id}&goal="))
        assertEquals(before.version, f.canvases.currentVersion(board))
        assertNull(vm.uiState.value.textDraft)
    }
    @Test fun reviewedDraftEditInvalidatesOldHashAndSaveReturnsRealSourceLinkedCard() = runBlocking {
        val board = f.canvases.create()
        val source = f.canvases.addTextNode(board, 0f, 0f, "Unchanged source")
        val vm = editor(board)
        vm.recognizedDraft(source, "Recognized text", "Initial review", "Unchanged source")
        val initial = vm.workflowStatus()
        val edited = vm.workflowReviewedText(initial.version, initial.reviewedDraftToken!!, initial.draftHash!!, "EDIT", "User reviewed correction") {}
        conflict { vm.workflowReviewedText(initial.version, initial.reviewedDraftToken, initial.draftHash, "SAVE", null) {} }
        assertEquals(1, f.canvases.snapshot(board).first.size)
        val saved = vm.workflowReviewedText(edited.version, edited.reviewedDraftToken!!, edited.draftHash!!, "SAVE", null) {}
        val state = requireNotNull(f.canvases.observe(board).first())
        val card = state.nodes.single { it.id == saved.nodeId }
        assertEquals(saved.noteId, card.refObjectId)
        assertEquals("User reviewed correction", f.notes.getNote(requireNotNull(saved.noteId))!!.body)
        assertEquals(source, state.edges.single { it.id == saved.edgeId }.fromNodeId)
        assertEquals("Unchanged source", state.nodes.single { it.id == source }.text)
        assertNull(vm.workflowStatus().reviewedDraftToken)
    }
    @Test fun sourcePolicyAndStaleBoardRejectBeforeExportCreatesAnything() = runBlocking {
        val board = f.canvases.create()
        f.canvases.addTextNode(board, 0f, 0f, "Private local text")
        val denied = editor(board, false)
        conflict { denied.workflowExport(denied.workflowStatus().version, false) {} }
        val vm = editor(board)
        val version = vm.workflowStatus().version
        f.canvases.addTextNode(board, 500f, 0f, "New card")
        conflict { vm.workflowExport(version, false) {} }
        assertTrue(f.notes.observeNotes().first().isEmpty())
    }
    @Test fun finalLeaseFailureRollsBackCreatedNoteCardAndEdgeTogether() = runBlocking {
        val board = f.canvases.create()
        val source = f.canvases.addTextNode(board, 0f, 0f, "Source")
        val version = requireNotNull(f.canvases.currentVersion(board))
        var attached = true
        try {
            f.canvases.withExpectedVersion(board, version, { if (!attached) throw CancellationException("View detached") }) {
                val note = f.notes.createNote("Should roll back")
                val card = f.canvases.addReference(board, note.id, 400f, 0f)
                f.canvases.connect(board, source, card, "reviewed text")
                attached = false
            }
            fail("Detached view cannot commit")
        } catch (_: CancellationException) { }
        assertTrue(f.notes.observeNotes().first().isEmpty())
        val state = requireNotNull(f.canvases.observe(board).first())
        assertEquals(listOf(source), state.nodes.map { it.id })
        assertTrue(state.edges.isEmpty())
        assertEquals(version, f.canvases.currentVersion(board))
    }
}
