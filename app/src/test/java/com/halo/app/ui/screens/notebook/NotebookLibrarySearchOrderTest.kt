package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
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

/** Actual Notebook screen and canonical isolated Room projection; no live graph or OS actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w900dp-h1800dp")
@OptIn(ExperimentalCoroutinesApi::class)
class NotebookLibrarySearchOrderTest {
    @get:Rule val compose = createComposeRule()
    private val scheduler = TestCoroutineScheduler()
    private val owners = ViewModelStore()
    private lateinit var fixture: WorkspaceFileToolsFixture
    private lateinit var model: NotebookViewModel
    private val resultIds = linkedMapOf<String, String>()
    private val navigation = CopyOnWriteArrayList<NotebookNavigation>()
    private var studyOpens = 0

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
        try {
            compose.runOnIdle { owners.clear() }
            scheduler.runCurrent()
        } finally {
            try { if (::fixture.isInitialized) fixture.close() } finally { Dispatchers.resetMain() }
        }
    }

    @Test fun librarySearchPlacesAllMatchingResultsBeforeSecondaryTools() {
        // A tall inspection viewport makes all three real result rows and Tools measurable
        // together. This proves rendered order, not a phone first-screen/scroll-count claim.
        render(height = 1800)
        enterSearch()
        compose.runOnIdle { model.toggleSelection(resultIds.getValue(NOTE_TITLE)) }
        val tools = compose.onNodeWithText("Tools").getUnclippedBoundsInRoot()
        resultIds.keys.forEach { title ->
            val result = compose.onNodeWithText(title).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("Matching result must precede secondary Tools: $title, result=$result tools=$tools",
                result.bottom <= tools.top)
        }
        compose.onNodeWithText(UNRELATED_TITLE).assertDoesNotExist()
        assertEquals(resultIds.values.toSet(), model.uiState.value.items.map { it.objectRef.id.value }.toSet())
        assertEquals(resultIds.keys.toList(), model.uiState.value.items.map { it.objectRef.title })
        assertEquals(NotebookSort.TITLE, model.uiState.value.sort)
        assertEquals(setOf(resultIds.getValue(NOTE_TITLE)), model.selectedIds.value)
        assertTrue(navigation.isEmpty())
        assertEquals(0, studyOpens)
    }

    @Test @Config(qualifiers = "w900dp-h400dp")
    fun constrainedLandscapeKeepsToolsAndMatchingObjectCallbacksReachableByScrolling() {
        render(height = 280)
        enterSearch()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Open Study"))
        compose.onNodeWithText("Open Study").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1, studyOpens)
        list.performScrollToNode(hasText("Memory"))
        compose.onNodeWithText("Memory").assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil(5_000) { scheduler.runCurrent(); navigation.size == 1 }
        assertEquals(NotebookNavigation.OpenMemoryHub(), navigation.single())
        list.performScrollToNode(hasText(NOTE_TITLE))
        compose.onNodeWithText(NOTE_TITLE).assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { scheduler.runCurrent(); navigation.size == 2 }
        assertEquals(NotebookNavigation.OpenNote(resultIds.getValue(NOTE_TITLE)), navigation.last())
        assertEquals(QUERY, model.queryText.value)
        assertEquals(NotebookSort.TITLE, model.uiState.value.sort)
        assertEquals(resultIds.values.toSet(), model.uiState.value.items.map { it.objectRef.id.value }.toSet())
        assertTrue(model.selectedIds.value.isEmpty())
        assertEquals(1, studyOpens)
    }

    @Test fun notebookHubKeepsContinueThenToolsThenOpenLibrary() {
        render(height = 1800)
        val continuation = compose.onNodeWithText("Continue").getUnclippedBoundsInRoot()
        val tools = compose.onNodeWithText("Tools").getUnclippedBoundsInRoot()
        val library = compose.onNodeWithText("Open Library").getUnclippedBoundsInRoot()
        assertTrue(continuation.bottom <= tools.top)
        assertTrue(tools.bottom <= library.top)
        compose.onNodeWithText("Open Study").assertIsDisplayed()
        assertEquals("", model.uiState.value.query)
        assertTrue(navigation.isEmpty())
        assertEquals(0, studyOpens)
    }

    private fun render(height: Int) {
        val preferences = AppLanguagePreferences(MemoryStorage(), "en")
        compose.setContent {
            AppLanguageProvider(preferences) {
                CompositionLocalProvider(LocalShellHaloOrb provides true) {
                    HostlessHaloTheme {
                        Box(Modifier.width(900.dp).height(height.dp)) {
                            NotebookScreen(model, onOpenStudy = { studyOpens++ })
                        }
                    }
                }
            }
        }
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.isLoaded }
        compose.waitForIdle()
    }

    private fun enterSearch() {
        compose.runOnIdle { model.setSort(NotebookSort.TITLE); model.setQuery(QUERY) }
        // Notebook's existing flowOn(Default) search debounce uses real elapsed time.
        // Pump only the VM scheduler; keep its canonical filtering/sort pipeline intact.
        compose.waitUntil(5_000) {
            scheduler.runCurrent()
            model.uiState.value.query == QUERY &&
                model.uiState.value.items.map { it.objectRef.id.value }.toSet() == resultIds.values.toSet()
        }
        compose.waitForIdle()
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
        private const val QUERY = "BOARD-D"
        private const val NOTE_TITLE = "BOARD-D alpha note"
        private const val CANVAS_TITLE = "BOARD-D beta canvas"
        private const val SECOND_NOTE_TITLE = "BOARD-D gamma note"
        private const val UNRELATED_TITLE = "Unrelated layout fixture"
    }
}
