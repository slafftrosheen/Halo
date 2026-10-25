package com.halo.app.ui.screens.notebook

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.CanvasNodeType
import com.halo.app.data.notebook.NotebookCanvasRepository
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.di.initializeUnitTestWorkManager
import com.halo.app.ui.presence.LocalShellHaloOrb
import com.halo.app.ui.theme.HostlessHaloTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real card rendering and selection owner, backed by the same Room repository as the editor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CanvasEditorSelectionInteractionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var repository: NotebookCanvasRepository
    private val store = ViewModelStore()
    private val vmScheduler = TestCoroutineScheduler()

    @Before fun setup() {
        // The VM's IO/Room continuations must not wait on Compose's separate
        // paused main dispatcher. Its save debounce is advanced explicitly.
        Dispatchers.setMain(UnconfinedTestDispatcher(vmScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        initializeUnitTestWorkManager(context)
        ManualDependencyGraph.init(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = NotebookCanvasRepository(db, db.notebookCanvasDao(), db.notebookDao())
    }

    @After fun close() {
        compose.runOnIdle { store.clear() }
        db.close()
        Dispatchers.resetMain()
    }

    private data class Board(val id: String, val card: String, val body: String, val vm: CanvasEditorViewModel)

    private fun persist(vm: CanvasEditorViewModel) {
        lateinit var job: kotlinx.coroutines.Job
        compose.runOnIdle { vmScheduler.advanceTimeBy(300); vmScheduler.runCurrent(); job = vm.persistPending() }
        compose.waitUntil(10_000) { vmScheduler.runCurrent(); job.isCompleted }
        assertFalse("Persistence must finish without cancellation", job.isCancelled)
    }

    private fun render(type: CanvasNodeType, selecting: Boolean): Board {
        val body = "Body selection ${type.name}"
        val (board, card) = runBlocking {
            val board = repository.create()
            val card = when (type) {
                CanvasNodeType.GROUP -> repository.addGroupNode(board, 24f, 24f, body)
                CanvasNodeType.TAG -> repository.addTagNode(board, 24f, 24f, body)
                else -> repository.addTextNode(board, 24f, 24f, body)
            }
            board to card
        }
        lateinit var vm: CanvasEditorViewModel
        compose.runOnIdle { vm = CanvasEditorViewModel(board, repository); store.put(board, vm) }
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellHaloOrb provides true) {
                CanvasEditorScreen(vm, onBack = {})
            }
        } }
        compose.waitUntil(10_000) { vmScheduler.runCurrent(); !vm.uiState.value.loading }
        if (selecting) compose.runOnIdle { vm.toggleSelectionMode() }
        compose.onNodeWithText(body, useUnmergedTree = true).assertIsDisplayed()
        return Board(board, card, body, vm)
    }

    private fun assertBodySelectsWithoutEditor(type: CanvasNodeType) {
        val board = render(type, selecting = true)
        val revision = runBlocking { db.notebookCanvasDao().getDocument(board.id)!!.revision }
        // A pointer tap on the glyphs reproduces the nested-field hit target; a
        // semantic click on the parent Surface would conceal the bug.
        compose.onNodeWithText(board.body, useUnmergedTree = true).performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(setOf(board.card), board.vm.uiState.value.selectedNodeIds)
            assertTrue(board.vm.uiState.value.selectionMode)
        }
        compose.onNode(hasText(board.body) and hasSetTextAction(), useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction() and SemanticsMatcher.expectValue(SemanticsProperties.Focused, true),
            useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText(board.body, useUnmergedTree = true).performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(board.vm.uiState.value.selectedNodeIds.isEmpty()) }
        persist(board.vm)
        assertEquals(board.body, runBlocking { repository.observe(board.id).first()!!.nodes.single().text })
        assertEquals(revision, runBlocking { db.notebookCanvasDao().getDocument(board.id)!!.revision })
    }

    @Test fun textBodyTapTogglesCanonicalSelectionWithoutTextWriteOrEditableFocus() = assertBodySelectsWithoutEditor(CanvasNodeType.TEXT)
    @Test fun groupTitleTapTogglesCanonicalSelectionWithoutTextWriteOrEditableFocus() = assertBodySelectsWithoutEditor(CanvasNodeType.GROUP)
    @Test fun tagTitleTapTogglesCanonicalSelectionWithoutTextWriteOrEditableFocus() = assertBodySelectsWithoutEditor(CanvasNodeType.TAG)

    @Test fun ordinaryTextEditingStillFocusesAndPersistsThroughTheSameOwner() {
        val board = render(CanvasNodeType.TEXT, selecting = false)
        compose.onNode(hasText(board.body) and hasSetTextAction(), useUnmergedTree = true)
            .performTouchInput { click(center) }
        compose.onNode(hasText(board.body) and hasSetTextAction(), useUnmergedTree = true)
            .performTextReplacement("Ordinary editing remains available")
        compose.runOnIdle { assertEquals(setOf(board.card), board.vm.uiState.value.selectedNodeIds) }
        persist(board.vm)
        assertEquals("Ordinary editing remains available", runBlocking { repository.observe(board.id).first()!!.nodes.single().text })
    }

    @Test fun shellOwnedCanvasHasNoHeaderOrDockAssistantLauncherButKeepsTools() {
        render(CanvasNodeType.TEXT, selecting = false)
        compose.onAllNodesWithContentDescription("Think with halo", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Tools").assertIsDisplayed().performClick()
        compose.onNodeWithText("Organize whole board").assertExists()
    }
}
