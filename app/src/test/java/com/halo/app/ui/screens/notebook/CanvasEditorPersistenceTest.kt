package com.halo.app.ui.screens.notebook

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.NotebookCanvasRepository
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
class CanvasEditorPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: NotebookCanvasRepository
    private val store = ViewModelStore()
    @Before fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        repository = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao())
    }
    @After fun close() { store.clear(); db.close(); Dispatchers.resetMain() }
    private suspend fun editor(board: String): CanvasEditorViewModel {
        val vm = CanvasEditorViewModel(board, repository)
        store.put("canvas", vm)
        withTimeout(5_000) { vm.uiState.first { !it.loading } }
        return vm
    }
    @Test fun erasedFinalStrokePersistsOnExistingCardAndClearsRecoveryDraft() = runBlocking {
        val board = repository.create()
        val original = "sketch:v1|100,100;200,200"
        val card = repository.addSketchNode(board, 0f, 0f, original)
        val ink = com.halo.sdk.runtime.ink.InkEditor(requireNotNull(com.halo.sdk.runtime.ink.InkCodec.decode(original)))
        assertTrue(ink.begin(com.halo.sdk.runtime.ink.InkTool.STYLUS, erasing = true))
        assertTrue(ink.sample(com.halo.sdk.runtime.ink.InkPoint(150, 150)))
        assertTrue(ink.finish())
        val erased = com.halo.sdk.runtime.ink.InkCodec.encode(ink.document)
        assertTrue(ink.document.strokes.isEmpty())
        val vm = editor(board)
        vm.keepDrawingDraft(com.halo.app.data.notebook.CanvasDrawingDraft(card, "sketch", original, sketch = erased)).join()
        vm.addSketch(erased, card, original).join()
        val reopened = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao())
        val node = reopened.observe(board).first()!!.nodes.single()
        assertEquals(card, node.id)
        assertEquals(erased, node.text)
        assertNull(reopened.drawingDraft(board))
        assertNull(vm.uiState.value.drawingDraft)
    }

    @Test fun erasedEditRejectsChangedSourceAndRetainsRecoveryDraft() = runBlocking {
        val board = repository.create()
        val original = "sketch:v1|100,100;200,200"
        val changed = "sketch:v1|300,300;400,400"
        val card = repository.addSketchNode(board, 0f, 0f, original)
        val vm = editor(board)
        val erased = com.halo.sdk.runtime.ink.InkCodec.encode(com.halo.sdk.runtime.ink.InkDocument())
        val draft = com.halo.app.data.notebook.CanvasDrawingDraft(card, "sketch", original, sketch = erased)
        vm.keepDrawingDraft(draft).join()
        assertTrue(repository.updateText(board, card, changed, original))
        vm.addSketch(erased, card, original).join()
        assertEquals(changed, repository.observe(board).first()!!.nodes.single().text)
        assertEquals(draft, repository.drawingDraft(board))
        assertNotNull(vm.uiState.value.notice)
    }

    @Test fun corruptSketchAndEmptyNewSketchCannotPublishOrDiscardDraft() = runBlocking {
        val board = repository.create()
        val original = "sketch:v1|100,100;200,200"
        val card = repository.addSketchNode(board, 0f, 0f, original)
        val vm = editor(board)
        val draft = com.halo.app.data.notebook.CanvasDrawingDraft(card, "sketch", original, sketch = original)
        vm.keepDrawingDraft(draft).join()
        vm.addSketch("sketch:v3|broken", card, original).join()
        vm.addSketch(com.halo.sdk.runtime.ink.InkCodec.encode(com.halo.sdk.runtime.ink.InkDocument())).join()
        assertTrue(runCatching { repository.updateText(board, card, "sketch:v3|broken", original) }.isFailure)
        assertTrue(runCatching { repository.addSketchNode(board, 0f, 0f, "sketch:v3|1400") }.isFailure)
        assertEquals(original, repository.observe(board).first()!!.nodes.single().text)
        assertEquals(draft, repository.drawingDraft(board))
    }
    @Test fun leavingImmediatelyFlushesLatestTextPositionAndViewport() = runBlocking {
        val board = repository.create()
        val card = repository.addTextNode(board, 0f, 0f, "original")
        val vm = editor(board)
        vm.updateNodeText(card, "first edit")
        vm.updateNodeText(card, "latest edit")
        vm.moveNode(card, 12f, 16f)
        vm.transform(3f, 4f, 1.5f)
        val saved = CompletableDeferred<Unit>()
        vm.leave { saved.complete(Unit) }
        withTimeout(5_000) { saved.await() }
        val persisted = repository.observe(board).first()!!
        assertEquals("latest edit", persisted.nodes.single().text)
        assertEquals(12f, persisted.nodes.single().x)
        assertEquals(16f, persisted.nodes.single().y)
        assertEquals(1.5f, persisted.viewportScale)
    }
    @Test fun selectingGroupAndMemberMovesEachOnlyOnce() = runBlocking {
        val board = repository.create()
        val group = repository.addGroupNode(board, 0f, 0f, "Group")
        val member = repository.addTextNode(board, 30f, 70f, "member")
        repository.connect(board, group, member, "group")
        val vm = editor(board)
        vm.toggleSelectionMode(); vm.selectNode(group); vm.selectNode(member)
        vm.moveNode(group, 10f, 20f)
        val saved = CompletableDeferred<Unit>()
        vm.leave { saved.complete(Unit) }
        withTimeout(5_000) { saved.await() }
        val nodes = repository.observe(board).first()!!.nodes.associateBy { it.id }
        assertEquals(10f, nodes.getValue(group).x)
        assertEquals(40f, nodes.getValue(member).x)
        assertEquals(90f, nodes.getValue(member).y)
    }
    @Test fun manualOrganizationWorksWithoutProviderAndCancelDoesNotMutate() = runBlocking {
        val board = repository.create()
        val card = repository.addTextNode(board, 0f, 0f, "idea")
        val vm = editor(board)
        assertEquals("Missing", vm.memoReadiness.value.phase)
        vm.selectNode(card)
        vm.organize()
        withTimeout(5_000) { vm.uiState.first { it.organization != null } }
        vm.cancelOrganization()
        assertNull(vm.uiState.value.organization)
        assertEquals(1, repository.observe(board).first()!!.nodes.size)
        vm.organize()
        withTimeout(5_000) { vm.uiState.first { it.organization != null } }
        vm.approveOrganization()
        withTimeout(5_000) { vm.uiState.first { it.undoAvailable } }
        assertTrue(repository.observe(board).first()!!.nodes.any { it.text == "Canvas" || it.type.name == "GROUP" })
    }
}
