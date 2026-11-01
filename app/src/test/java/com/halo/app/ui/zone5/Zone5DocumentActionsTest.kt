package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.annotation.GraphicsMode
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.halo.app.ui.theme.HaloPalette
import com.halo.app.ui.theme.HaloAccent
import com.halo.app.ui.theme.HaloTone
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.halo.app.data.workspace.WorkspaceFileToolsFixture
import com.halo.app.notebook.NotebookUseCases
import com.halo.app.ui.presence.*
import com.halo.app.ui.screens.notebook.*
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.app.ui.workspace.WorkspaceFileToolsScreen
import com.halo.app.ui.workspace.WorkspaceFileToolsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual entry-scoped page projections backed by isolated canonical Room owners. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
@OptIn(ExperimentalCoroutinesApi::class)
class Zone5DocumentActionsTest {
    @get:Rule val compose = createComposeRule()
    private val scheduler = TestCoroutineScheduler()
    private val owners = ViewModelStore()
    private val jobs = mutableListOf<Job>()
    private lateinit var fixture: WorkspaceFileToolsFixture
    private val actions = mutableStateOf<ShellPageProjection?>(null)

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        fixture = WorkspaceFileToolsFixture()
    }
    private fun own(model: ViewModel) { owners.put("owner", model); jobs += requireNotNull(model.viewModelScope.coroutineContext[Job]) }
    @After fun close() {
        try {
            compose.runOnIdle { owners.clear() }; scheduler.runCurrent()
            runBlocking { withTimeout(10_000) { jobs.forEach { it.join() }; fixture.scope.coroutineContext[Job]?.cancelAndJoin() } }
        } finally { try { fixture.close() } finally { Dispatchers.resetMain() } }
    }
    private fun render(route: String, scale: Float = 0f, content: @Composable () -> Unit) {
        compose.setContent { HostlessHaloTheme(darkTheme = true, fontScale = scale, palette = HaloPalette(HaloAccent.BLUE, HaloTone.COOL)) {
            CompositionLocalProvider(LocalShellHaloOrb provides true, LocalShellPageActions provides actions) {
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
                    val nav = rememberNavController()
                    NavHost(nav, route) { composable(route) { content() } }
                }
            }
        } }
        compose.waitUntil(5_000) { scheduler.runCurrent(); actions.value != null }
    }
    @Test fun noteDefaultActionsBelongToTheCurrentDocument() {
        val note = runBlocking { fixture.notes.createNote("Owned document content.", title = "Light and sound") }
        lateinit var model: NoteEditorViewModel
        compose.runOnUiThread { model = NoteEditorViewModel(note.id, NotebookUseCases(fixture.notes), document = fixture.documents); own(model) }
        render("notebook/note/{noteId}") { NoteEditorScreen(model, {}) }
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.status == SaveStatus.SAVED }
        compose.onRoot().captureRoboImage()
        compose.runOnIdle {
            assertEquals(note.id, actions.value!!.owner.objectId)
            assertNotNull(actions.value!!.owner.navigationEntryId)
            assertEquals(listOf("page.note.insert", "page.note.outline", "page.note.find"), actions.value!!.actions.take(3).map { it.id })
        }
    }
    @Test fun readEditNavigationRetainsEachScrollAndDirtySourceNeedsExplicitDiscard() {
        val note = runBlocking { fixture.notes.createNote((1..100).joinToString("\n\n") { "Paragraph $it: retained reading and editing position." }, title = "Long fixture") }
        lateinit var model: NoteEditorViewModel
        compose.runOnUiThread { model = NoteEditorViewModel(note.id, NotebookUseCases(fixture.notes), document = fixture.documents); own(model) }
        render("notebook/note/{noteId}") { NoteEditorScreen(model, {}) }
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.status == SaveStatus.SAVED }
        val editing = compose.onNode(hasScrollToIndexAction())
        editing.performScrollToIndex(40)
        val editPosition = editing.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(editPosition > 1f)
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.note.view-read")) }
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        val reading = compose.onNode(hasScrollAction())
        val readPosition = reading.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(readPosition > 0f)
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.note.view-edit")) }
        assertEquals(editPosition, compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value(), .1f)
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.note.view-read")) }
        assertEquals(readPosition, compose.onNode(hasScrollAction()).fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value(), .1f)
        compose.runOnIdle { model.setMode(EditorMode.SOURCE); model.updateSourceDraft("Unapplied source that must survive") }
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.note.view-read")) }
        compose.onNodeWithText("Discard the unapplied source edit?").assertIsDisplayed()
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(EditorMode.SOURCE, model.uiState.value.mode)
        assertEquals("Unapplied source that must survive", model.uiState.value.sourceDraft)
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.note.view-edit")) }
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.mode == EditorMode.EDIT }
        assertTrue(model.uiState.value.displayBody.contains("Paragraph 40"))
    }
    @Test fun officeLandingHasCreationOpenAndTruthfullyDisabledExport() {
        lateinit var model: WorkspaceFileToolsViewModel
        compose.runOnUiThread { model = WorkspaceFileToolsViewModel(fixture.tools); own(model) }
        render("file_tools") { WorkspaceFileToolsScreen(model, {}, {}, {}, {}, {}, {}) }
        compose.onRoot().captureRoboImage()
        compose.runOnIdle {
            assertEquals(listOf("page.office.new", "page.office.open", "page.office.export"), actions.value!!.actions.take(3).map { it.id })
            assertFalse(actions.value!!.actions.single { it.id == "page.office.export" }.enabled)
            assertFalse(actions.value!!.invoke("page.office.export"))
        }
    }
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp")
    fun noteExportChoiceKeepsReadingScaleAt200Percent() = checkNoteExport(false)

    @Test @Config(qualifiers = "pl-rPL-w360dp-h800dp")
    fun noteExportReceiptKeepsReadingScaleAt200Percent() = checkNoteExport(true)

    private fun checkNoteExport(finish: Boolean) {
        val note = runBlocking { fixture.notes.createNote("A durable local note.", title = "Field notes") }
        lateinit var model: NoteEditorViewModel
        var output: com.halo.app.data.workspace.WorkspaceFile? = null
        var cancel = ""
        compose.runOnUiThread { model = NoteEditorViewModel(note.id, NotebookUseCases(fixture.notes), document = fixture.documents); own(model) }
        render("notebook/note/{noteId}", scale = 2f) {
            cancel = com.halo.app.language.uiText("Cancel")
            NoteEditorScreen(model, {}, onExportCopy = { id, revision, format ->
                fixture.text.exportNote(id, revision, format).also { output = it }
            })
        }
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.status == SaveStatus.SAVED }
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.note.export")) }
        if (finish) {
            compose.onNodeWithText("TXT").performScrollTo().performClick()
            compose.waitUntil(5_000) { scheduler.runCurrent(); output != null }
            assertTrue(runBlocking { fixture.bytes(requireNotNull(output)).decodeToString() }.contains("A durable local note."))
        }
        val title = fixture.context.getString(if (finish) com.halo.app.R.string.file52_output_saved else com.halo.app.R.string.note_export_copy)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNode(hasText(title) and hasAnyAncestor(isDialog()))
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2f, layouts.single().layoutInput.density.fontScale, 0.001f)
        compose.onNode(isDialog()).captureRoboImage()
        compose.onNodeWithText(if (finish) fixture.context.getString(com.halo.app.R.string.file52_done)
            else cancel).assertIsDisplayed().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        if (!finish) assertNull(output)
    }
    @Test fun insertingABlockUsesTheExistingDocumentOwnerAndAChangedSourceDisablesTheSheet() {
        val note = runBlocking { fixture.notes.createNote("Original paragraph.", title = "Field notes") }
        lateinit var model: NoteEditorViewModel
        compose.runOnUiThread { model = NoteEditorViewModel(note.id, NotebookUseCases(fixture.notes), document = fixture.documents); own(model) }
        render("notebook/note/{noteId}") { NoteEditorScreen(model, {}) }
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.status == SaveStatus.SAVED }
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.note.insert")) }
        compose.onNodeWithText("Heading 1").performScrollTo().performClick()
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.blocks.size == 2 }
        assertEquals(com.halo.app.data.notebook.NoteBlockType.HEADING, model.uiState.value.blocks.last().type)
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.note.insert")); model.updateBlock(model.uiState.value.blocks.first().id, "Updated paragraph.") }
        compose.onNodeWithText("Heading 1").assertIsNotEnabled()
        assertEquals(2, model.uiState.value.blocks.size)
    }

}
