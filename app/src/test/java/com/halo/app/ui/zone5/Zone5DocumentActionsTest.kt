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
    private fun render(route: String, content: @Composable () -> Unit) {
        compose.setContent { HostlessHaloTheme(darkTheme = true, palette = HaloPalette(HaloAccent.BLUE, HaloTone.COOL)) {
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
