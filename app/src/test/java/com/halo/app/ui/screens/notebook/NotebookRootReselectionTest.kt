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

/** Root presentation reset against isolated canonical owners; unexecuted regression source. */
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

    @Before fun setup() {
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

    @Test fun sameRouteRootRequestLeavesLibraryQueryFilterAndSelectionIntact() {
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellHaloOrb provides true, LocalShellPageActions provides actions) {
                Box(Modifier.width(900.dp).height(1800.dp)) {
                    NotebookScreen(model, onOpenStudy = {}, rootRequest = rootRequest.value)
                }
            }
        } }
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.isLoaded }
        compose.runOnIdle {
            model.setQuery(QUERY)
            model.setFilter(NotebookFilter.NOTES)
            model.toggleSelection(resultIds.getValue(NOTE_TITLE))
        }
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.query == QUERY }
        for (suite in listOf("office", "studio")) {
            compose.runOnIdle { assertTrue(actions.value!!.invoke("page.notebook.$suite")) }
            lateinit var nested: ShellPageProjection
            compose.runOnIdle { nested = actions.value!!; assertEquals(suite, nested.owner.activeToolId); rootRequest.value++ }
            compose.runOnIdle {
                assertEquals("library", actions.value!!.owner.activeToolId)
                assertFalse(nested.invoke("page.notebook.suites"))
                assertEquals(QUERY, model.queryText.value)
                assertEquals(NotebookFilter.NOTES, model.uiState.value.filter)
                assertEquals(setOf(resultIds.getValue(NOTE_TITLE)), model.selectedIds.value)
                assertTrue(navigation.isEmpty())
            }
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
        compose.onNodeWithText("Halo Studio").performClick()
        compose.onNodeWithText("Office Tools").performClick()
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
        compose.setContent { HostlessHaloTheme {
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
        override suspend fun search(query: WorkspaceSearchQuery) = WorkspaceSearchResult(query.query, emptyList())
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
