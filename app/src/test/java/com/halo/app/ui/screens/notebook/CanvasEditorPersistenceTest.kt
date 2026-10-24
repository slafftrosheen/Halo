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
    @Test fun editorAndAssistantGroupMovesPersistTheSameMemberPositions() = runBlocking {
        for (explicit in listOf(false, true)) {
            val results = mutableListOf<List<Triple<String, Float, Float>>>()
            for (assistant in listOf(false, true)) {
                val board = repository.create()
                val group = repository.addGroupNode(board, 0f, 0f, "Group")
                repository.addTextNode(board, 10f, 20f, "Inside")
                val outside = repository.addTextNode(board, 900f, 900f, "Outside")
                repository.addGroupNode(board, 20f, 20f, "Nested")
                if (explicit) repository.connect(board, group, outside, "group")
                if (assistant) {
                    val action = com.halo.app.di.tools.WorkspaceCanvasActions(db, repository)
                    val version = "canvas:${db.notebookCanvasDao().getDocument(board)!!.revision}"
                    assertTrue(action.execute(com.halo.sdk.provider.ToolCall("move", "halo_canvas_move", mapOf("canvasId" to board, "version" to version, "nodeId" to group, "x" to 100, "y" to 200))) is com.halo.runtime.cognition.tools.ToolExecutionResult.Success)
                } else {
                    val vm = editor(board)
                    vm.beginDrag(group)
                    vm.moveNode(group, 100f, 200f)
                    vm.persistPending().join()
                }
                results += repository.observe(board).first()!!.nodes.map { Triple(it.text, it.x, it.y) }.sortedBy { it.first }
            }
            assertEquals(results[0], results[1])
        }
    }
    @Test fun assistantClearCannotBeReversedByAnAlreadyOpenEditorOrItsRecoveryDraft() = runBlocking {
        val board = repository.create()
        val original = "sketch:v1|100,100;200,200"
        val edited = "sketch:v1|300,300;400,400"
        val card = repository.addSketchNode(board, 0f, 0f, original)
        val draft = com.halo.app.data.notebook.CanvasDrawingDraft(card, "sketch", original, sketch = edited)
        repository.saveDrawingDraft(board, draft)
        val vm = editor(board)
        val version = "canvas:${db.notebookCanvasDao().getDocument(board)!!.revision}"
        val action = com.halo.app.di.tools.WorkspaceCanvasActions(db, repository)
        assertTrue(action.execute(com.halo.sdk.provider.ToolCall("clear", "halo_canvas_clear_sketch", mapOf("canvasId" to board, "version" to version, "nodeId" to card))) is com.halo.runtime.cognition.tools.ToolExecutionResult.Success)
        vm.addSketch(edited, card, original, draft).join()
        val cleared = repository.observe(board).first()!!.nodes.single().text
        assertTrue(requireNotNull(com.halo.sdk.runtime.ink.InkCodec.decode(cleared)).strokes.isEmpty())
        assertFalse(draft.matchesSource(card, "sketch", cleared))
        assertEquals(draft, repository.drawingDraft(board))
        val reopened = editor(board)
        assertEquals(cleared, reopened.uiState.value.nodes.single().text)
    }
    @Test fun savingOrCancellingAnotherSketchRetainsTheUnrelatedRecoveryDraft() = runBlocking {
        val board = repository.create()
        val original = "sketch:v1|100,100;200,200"
        val edited = "sketch:v1|300,300;400,400"
        val first = repository.addSketchNode(board, 0f, 0f, original)
        val other = repository.addSketchNode(board, 0f, 0f, original)
        val draft = com.halo.app.data.notebook.CanvasDrawingDraft(other, "sketch", original, sketch = edited)
        repository.saveDrawingDraft(board, draft)
        val vm = editor(board)
        vm.discardDrawingDraft(null).join()
        assertEquals(draft, repository.drawingDraft(board))
        vm.addSketch(edited, first, original).join()
        assertEquals(edited, repository.observe(board).first()!!.nodes.single { it.id == first }.text)
        assertEquals(draft, repository.drawingDraft(board))
        vm.addSketch(edited).join()
        assertEquals(3, repository.observe(board).first()!!.nodes.size)
        assertEquals(draft, repository.drawingDraft(board))
        vm.discardDrawingDraft(draft).join()
        assertNull(repository.drawingDraft(board))
    }

    @Test fun reopeningChangedSketchCannotConsumeItsOlderRecoveryDraft() = runBlocking {
        val board = repository.create()
        val old = "sketch:v1|100,100;200,200"
        val current = "sketch:v1|300,300;400,400"
        val edited = "sketch:v1|500,500;600,600"
        val card = repository.addSketchNode(board, 0f, 0f, current)
        val draft = com.halo.app.data.notebook.CanvasDrawingDraft(card, "sketch", old, sketch = edited)
        repository.saveDrawingDraft(board, draft)
        val vm = editor(board)
        vm.discardDrawingDraft(null).join()
        assertEquals(draft, repository.drawingDraft(board))
        vm.addSketch(edited, card, current).join()
        assertEquals(edited, repository.observe(board).first()!!.nodes.single().text)
        assertEquals(draft, repository.drawingDraft(board))
        assertEquals(draft, vm.uiState.value.drawingDraft)
    }

    @Test fun annotationSaveClearsOnlyARecoveryDraftForTheExactFileVersion() = runBlocking {
        val ink = "sketch:v1|100,100;200,200"
        val changedInk = "sketch:v1|300,300;400,400"
        for ((index, fixture) in listOf("file-v1" to ink, "file-v2" to ink, "file-v2" to changedInk).withIndex()) {
            val (draftVersion, draftInk) = fixture
            val board = repository.create()
            val fileId = "annotation-source-$index"
            db.notebookDao().insertWorkspaceObject(com.halo.app.data.notebook.WorkspaceObjectEntity(
                fileId, "FILE", "Worksheet", 1, 1, "IMPORT", "ACTIVE", true, "READY", "file:$fileId"))
            db.workspaceFileDao().insertFile(com.halo.app.data.workspace.WorkspaceFileEntity(
                objectId = fileId, uri = "content://fixture/$fileId", displayName = "Worksheet", mimeType = "application/pdf",
                sizeBytes = 4, modifiedAt = 1, ownedCopyPath = null, permissionPersisted = true, sha256 = "file-v2"))
            val card = repository.addReference(board, fileId, 0f, 0f)
            val original = repository.observe(board).first()!!.nodes.single().text
            val draft = com.halo.app.data.notebook.CanvasDrawingDraft(card, "annotation", original,
                pages = mapOf(1 to draftInk), fileVersion = draftVersion)
            repository.saveDrawingDraft(board, draft)
            val vm = editor(board)
            vm.saveFileMarkup(card, mapOf(1 to ink), original, "file-v2", expectedDraft = draft).join()
            assertEquals(setOf(1), com.halo.app.data.notebook.CanvasFileMarkupCodec.decode(
                repository.observe(board).first()!!.nodes.single().text).pages.keys)
            assertEquals(if (draftVersion == "file-v2" && draftInk == ink) null else draft, repository.drawingDraft(board))
        }
    }

    @Test fun olderSaveAndCancelCannotConsumeANewerDraftForTheSameSource() = runBlocking {
        val board = repository.create()
        val original = "sketch:v1|100,100;200,200"
        val olderInk = "sketch:v1|300,300;400,400"
        val newerInk = "sketch:v1|500,500;600,600"
        val card = repository.addSketchNode(board, 0f, 0f, original)
        val older = com.halo.app.data.notebook.CanvasDrawingDraft(card, "sketch", original, sketch = olderInk)
        val newer = older.copy(sketch = newerInk)
        repository.saveDrawingDraft(board, newer)
        val vm = editor(board)
        vm.discardDrawingDraft(older).join()
        assertEquals(newer, repository.drawingDraft(board))
        vm.addSketch(olderInk, card, original, expectedDraft = older).join()
        assertEquals(olderInk, repository.observe(board).first()!!.nodes.single().text)
        assertEquals(newer, repository.drawingDraft(board))
        assertEquals(newer, vm.uiState.value.drawingDraft)
    }

    @Test fun olderSaveAndCancelCannotConsumeAnIdenticalDraftFromANewEditor() = runBlocking {
        val board = repository.create()
        val original = "sketch:v1|100,100;200,200"
        val edited = "sketch:v1|300,300;400,400"
        val card = repository.addSketchNode(board, 0f, 0f, original)
        val older = com.halo.app.data.notebook.CanvasDrawingDraft(card, "sketch", original, sketch = edited)
        val newer = com.halo.app.data.notebook.CanvasDrawingDraft(card, "sketch", original, sketch = edited)
        repository.saveDrawingDraft(board, newer)
        val vm = editor(board)
        vm.discardDrawingDraft(older).join()
        assertEquals(newer, repository.drawingDraft(board))
        vm.addSketch(edited, card, original, expectedDraft = older).join()
        assertEquals(edited, repository.observe(board).first()!!.nodes.single().text)
        assertEquals(newer, repository.drawingDraft(board))
        assertEquals(newer, vm.uiState.value.drawingDraft)
        vm.discardDrawingDraft(newer).join()
        assertNull(repository.drawingDraft(board))
    }

    @Test fun observedDraftCannotBeConsumedBySavingADifferentSketchPayload() = runBlocking {
        val board = repository.create()
        val original = "sketch:v1|100,100;200,200"
        val savedInk = "sketch:v1|300,300;400,400"
        val draftInk = "sketch:v1|500,500;600,600"
        val card = repository.addSketchNode(board, 0f, 0f, original)
        val observed = com.halo.app.data.notebook.CanvasDrawingDraft(card, "sketch", original, sketch = draftInk)
        repository.saveDrawingDraft(board, observed)
        val vm = editor(board)
        vm.addSketch(savedInk, card, original, expectedDraft = observed).join()
        assertEquals(savedInk, repository.observe(board).first()!!.nodes.single().text)
        assertEquals(observed, repository.drawingDraft(board))
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
        val draft = com.halo.app.data.notebook.CanvasDrawingDraft(card, "sketch", original, sketch = erased)
        vm.keepDrawingDraft(draft).join()
        vm.addSketch(erased, card, original, expectedDraft = draft).join()
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
