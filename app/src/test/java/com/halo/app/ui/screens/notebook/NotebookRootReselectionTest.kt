package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.halo.app.ui.presence.LocalShellPageActions
import com.halo.app.ui.presence.ShellPageProjection
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.composable
import com.halo.app.data.RoomConversationStore
import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.memory.RoomMemoryRepository
import com.halo.app.data.notebook.NotebookOrganizationRepository
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.search.WorkspaceSearchEngine
import com.halo.app.data.search.WorkspaceSearchQuery
import com.halo.app.data.search.WorkspaceSearchResult
import com.halo.app.data.workspace.WorkspaceFileToolsFixture
import com.halo.app.language.AppLanguagePreferences
import com.halo.app.language.AppLanguageProvider
import com.halo.app.notebook.NotebookUseCases
import com.halo.app.notebook.WorkspaceUseCases
import com.halo.app.ui.presence.LocalShellHaloOrb
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.sdk.runtime.Storage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Root presentation reset against isolated canonical owners. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w900dp-h1800dp")
@OptIn(ExperimentalCoroutinesApi::class)
class NotebookRootReselectionTest {
    @get:Rule val compose = createComposeRule()
    private val scheduler = TestCoroutineScheduler()
    private val owners = ViewModelStore()
    private lateinit var fixture: WorkspaceFileToolsFixture
    private lateinit var model: NotebookViewModel
    private val resultIds = linkedMapOf<String, String>()
    private val navigation = CopyOnWriteArrayList<NotebookNavigation>()
    private val actions = mutableStateOf<ShellPageProjection?>(null)
    private val rootRequest = mutableStateOf(0L)
    private val restoration = StateRestorationTester(compose)

    @Before fun setup() {
        NoDerivedSearchHits.unavailable = false
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        fixture = WorkspaceFileToolsFixture()
        val preferences = MemoryStorage()
        val notes = NotebookUseCases(fixture.notes)
        val conversations = RoomConversationStore(fixture.database, fixture.scope)
        runBlocking {
            conversations.awaitReady()
            resultIds[NOTE_TITLE] = fixture.notes.createNote("Owned synthetic search fixture.", title = NOTE_TITLE).id
            resultIds[CANVAS_TITLE] = fixture.canvases.create(CANVAS_TITLE)
            resultIds[SECOND_NOTE_TITLE] = fixture.notes.createNote("Second owned fixture.", title = SECOND_NOTE_TITLE).id
            fixture.notes.createNote("Not a match.", title = UNRELATED_TITLE)
        }
        val workspace = WorkspaceUseCases(fixture.workspace, NoDerivedSearchHits,
            WorkspaceContextStore(fixture.database, fixture.notes, preferences), conversations,
            fixture.files, RoomMemoryRepository(fixture.database), notes)
        compose.runOnUiThread {
            model = NotebookViewModel(workspace, notes,
                NotebookOrganizationRepository(fixture.database.notebookOrganizationDao()), fixture.canvases)
            owners.put("notebook", model)
        }
        fixture.scope.launch { model.navigationEvents.collect { navigation += it } }
    }

    @After fun close() {
        val work = if (::model.isInitialized) model.viewModelScope.coroutineContext[Job] else null
        try {
            compose.runOnIdle { owners.clear() }
            scheduler.runCurrent()
            runBlocking {
                withTimeout(10_000) {
                    work?.join()
                    if (::fixture.isInitialized) fixture.scope.coroutineContext[Job]?.cancelAndJoin()
                }
            }
        } finally {
            try { if (::fixture.isInitialized) fixture.close() } finally { Dispatchers.resetMain() }
        }
    }

    @Test fun localLibraryToolsRoundTripPreservesQueryFilterAndSelection() {
        renderWorkspace()
        compose.runOnIdle {
            model.setQuery(QUERY)
            model.setFilter(NotebookFilter.NOTES)
            model.toggleSelection(resultIds.getValue(NOTE_TITLE))
        }
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.query == QUERY }
        repeat(2) {
            val library = actions.value!!
            compose.onNodeWithText("Tools").performClick()
            compose.runOnIdle { assertEquals("tools", actions.value!!.owner.activeToolId); assertFalse(library.invoke("page.notebook.new-note")) }
            compose.onNodeWithText("Library").performClick()
            compose.runOnIdle {
                assertEquals("library", actions.value!!.owner.activeToolId)
                assertEquals(QUERY, model.queryText.value)
                assertEquals(NotebookFilter.NOTES, model.uiState.value.filter)
                assertEquals(setOf(resultIds.getValue(NOTE_TITLE)), model.selectedIds.value)
                assertTrue(navigation.isEmpty())
            }
        }
    }

    @Test fun toolsTabRestoresAfterRecreation() {
        renderWorkspace()
        compose.onNodeWithText("Tools").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals("tools", actions.value!!.owner.activeToolId); assertTrue(actions.value!!.actions.isEmpty()) }
        compose.onNodeWithText("Office Tools", ignoreCase = true).assertIsDisplayed()
    }

    @Test fun failedLibrarySearchKeepsNavigationUsableAndCanRetry() {
        renderWorkspace()
        compose.runOnIdle { NoDerivedSearchHits.unavailable = true; model.setQuery(QUERY) }
        compose.waitUntil(5_000) {
            scheduler.runCurrent()
            compose.onAllNodesWithText("Library unavailable").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Tools").performClick()
        compose.onNodeWithText("Study").assertIsDisplayed()
        compose.onNodeWithText("Library").performClick()
        compose.runOnIdle { NoDerivedSearchHits.unavailable = false }
        compose.onNodeWithText("Try again").performClick()
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.resolvedQuery == QUERY && model.uiState.value.isLoaded &&
            compose.onAllNodesWithText("Library unavailable").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Library unavailable").assertDoesNotExist()
        assertEquals(QUERY, model.queryText.value)
    }

    @Test fun aFailedRefreshCannotAdmitActionsAgainstOtherwiseMatchingCachedResults() {
        val library = NotebookLibrarySession { true }
        renderWorkspace(library)
        compose.runOnIdle { model.setQuery(QUERY) }
        compose.waitUntil(5_000) { scheduler.runCurrent(); library.read()?.view?.settled == true && model.uiState.value.resolvedQuery == QUERY }
        val selectedBefore = setOf(resultIds.getValue(NOTE_TITLE))
        compose.runOnIdle { model.toggleSelection(resultIds.getValue(NOTE_TITLE)); NoDerivedSearchHits.unavailable = true }
        runBlocking { fixture.notes.createNote("New synthetic result.", title = "$QUERY added") }
        compose.waitUntil(5_000) {
            scheduler.runCurrent()
            compose.onAllNodesWithText("Library unavailable").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Select visible").assertIsNotEnabled()
        compose.onNodeWithText("Actions").assertIsNotEnabled()
        compose.runOnIdle {
            val frame = library.read()!!
            assertTrue("The last usable list stays visible", frame.view.rendered.isLoaded)
            assertEquals(QUERY, frame.view.rendered.resolvedQuery)
            assertFalse("A failed refresh is not settled", frame.view.settled)
            assertNull(library.set(frame.snapshot, NotebookLibraryChange.Selection(setOf(resultIds.getValue(SECOND_NOTE_TITLE)))))
            assertEquals(selectedBefore, model.selectedIds.value)
        }
    }

    @Test fun libraryStartsWithCreationImportAndVisibleOrganization() {
        renderWorkspace()
        compose.runOnIdle {
            assertEquals(listOf("page.notebook.new-note", "page.notebook.import", "page.notebook.organize"),
                actions.value!!.actions.take(3).map { it.id })
            assertTrue(actions.value!!.invoke("page.notebook.organize"))
        }
        compose.onNodeWithText("0 selected").assertExists()
    }

    @Test fun toolsHaveNoLibraryLeaseAndOpenEachActualInstrumentInOneClick() {
        val library = NotebookLibrarySession { true }
        var study = 0; var studio = 0; var office = 0
        renderWorkspace(library, { study++ }, { studio++ }, { office++ })
        compose.waitUntil(5_000) { scheduler.runCurrent(); library.read() != null }
        compose.onNodeWithText("Tools").performClick()
        compose.runOnIdle {
            assertEquals("tools", actions.value!!.owner.activeToolId)
            assertTrue(actions.value!!.actions.isEmpty())
            assertNull("Hidden Library cannot remain an assistant target", library.read())
        }
        compose.onNodeWithText("Study").performClick()
        compose.onNodeWithText("Halo Studio", ignoreCase = true).performClick()
        compose.onNodeWithText("Office Tools", ignoreCase = true).performClick()
        compose.onNodeWithText("Memory").performClick()
        compose.waitUntil(5_000) { scheduler.runCurrent(); navigation.any { it is NotebookNavigation.OpenMemoryHub } }
        assertEquals(1, study); assertEquals(1, studio); assertEquals(1, office)
    }

    @Test fun rootReselectionReturnsToolsToLibraryWithoutDiscardingQueryOrSelection() {
        renderWorkspace()
        compose.runOnIdle {
            model.setQuery(QUERY); model.setFilter(NotebookFilter.NOTES)
            model.toggleSelection(resultIds.getValue(NOTE_TITLE))
        }
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.query == QUERY }
        compose.onNodeWithText("Tools").performClick()
        lateinit var previous: ShellPageProjection
        compose.runOnIdle { previous = actions.value!!; rootRequest.value++ }
        compose.runOnIdle {
            assertEquals("library", actions.value!!.owner.activeToolId)
            assertEquals(QUERY, model.queryText.value)
            assertEquals(setOf(resultIds.getValue(NOTE_TITLE)), model.selectedIds.value)
            assertNotSame(previous, actions.value)
        }
    }

    private fun renderWorkspace(library: NotebookLibrarySession? = null,
        study: () -> Unit = {}, studio: () -> Unit = {}, office: () -> Unit = {}) {
        restoration.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellHaloOrb provides true, LocalShellPageActions provides actions) {
                Box(Modifier.width(900.dp).height(1800.dp)) {
                    val nav = androidx.navigation.compose.rememberNavController()
                    androidx.navigation.compose.NavHost(nav, startDestination = "notebook") {
                        composable("notebook") {
                            NotebookScreen(model, onOpenStudy = study, onOpenStudio = studio, onOpenFileTools = office,
                                rootRequest = rootRequest.value, librarySession = library)
                        }
                    }
                }
            }
        } }
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.isLoaded }
    }

    /** No platform index/provider IO; Notebook's actual canonical title-match path is exercised. */
    private object NoDerivedSearchHits : WorkspaceSearchEngine {
        @Volatile var unavailable = false
        override suspend fun search(query: WorkspaceSearchQuery): WorkspaceSearchResult {
            if (unavailable) throw java.io.IOException("Synthetic unavailable index")
            return WorkspaceSearchResult(query.query, emptyList())
        }
        override suspend fun indexObject(objectId: WorkspaceObjectId, type: WorkspaceObjectType,
            title: String, content: String, updatedAt: Long) = Unit
        override suspend fun removeObject(objectId: WorkspaceObjectId) = Unit
    }

    private class MemoryStorage : Storage {
        private val values = ConcurrentHashMap<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }

    companion object {
        private const val QUERY = "root-fixture"
        private const val NOTE_TITLE = "root-fixture note"
        private const val CANVAS_TITLE = "root-fixture canvas"
        private const val SECOND_NOTE_TITLE = "root-fixture second note"
        private const val UNRELATED_TITLE = "Unrelated fixture"
    }
}
