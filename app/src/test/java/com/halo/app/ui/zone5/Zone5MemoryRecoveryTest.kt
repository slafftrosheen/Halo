package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.halo.app.data.RoomConversationStore
import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.memory.*
import com.halo.app.data.notebook.NotebookOrganizationRepository
import com.halo.app.data.search.*
import com.halo.app.data.workspace.WorkspaceFileToolsFixture
import com.halo.app.notebook.*
import com.halo.app.ui.presence.*
import com.halo.app.ui.screens.notebook.*
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.runtime.cognition.memory.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w900dp-h1400dp")
@OptIn(ExperimentalCoroutinesApi::class)
class Zone5MemoryRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val scheduler = TestCoroutineScheduler()
    private val owners = ViewModelStore()
    private val jobs = mutableListOf<Job>()
    private lateinit var fixture: WorkspaceFileToolsFixture
    private lateinit var memories: RoomMemoryRepository
    private lateinit var model: MemoryHubViewModel
    private val retriever = object : MemoryRetriever {
        @Volatile var unavailable = false
        override suspend fun search(query: String, scope: MemorySearchScope, limit: Int): List<MemorySearchResult> {
            if (unavailable) throw java.io.IOException("Synthetic unavailable index")
            return emptyList()
        }
    }
    private val actions = mutableStateOf<ShellPageProjection?>(null)
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler)); fixture = WorkspaceFileToolsFixture()
        memories = RoomMemoryRepository(fixture.database)
        runBlocking { memories.promoteCandidate(MemoryCandidate("Basil needs bright light.")) }
        compose.runOnUiThread {
            model = MemoryHubViewModel(memories, retriever, PersonalLearningRepository(fixture.database, memories),
                MutableStateFlow(false), {}, { _, _ -> null }, { emptyList() })
            own("memory", model)
        }
    }
    private fun own(key: String, model: ViewModel) { owners.put(key, model); jobs += requireNotNull(model.viewModelScope.coroutineContext[Job]) }
    @After fun close() {
        try {
            compose.runOnIdle { owners.clear() }; scheduler.runCurrent()
            runBlocking { withTimeout(10_000) { jobs.forEach { it.join() }; fixture.scope.coroutineContext[Job]?.cancelAndJoin() } }
        } finally { try { fixture.close() } finally { Dispatchers.resetMain() } }
    }
    private fun await(predicate: () -> Boolean) = compose.waitUntil(5_000) { scheduler.advanceTimeBy(300); scheduler.runCurrent(); predicate() }

    @Test fun failedSearchCanRecoverWhenTheQueryChanges() {
        compose.setContent { HostlessHaloTheme { val state by model.state.collectAsStateWithLifecycle(); Text(state.query) } }
        await { !model.state.value.loading }
        compose.runOnIdle { retriever.unavailable = true; model.search("broken") }
        await { model.state.value.error != null }
        compose.runOnIdle { retriever.unavailable = false; model.search("recovered") }
        await { !model.state.value.loading && model.state.value.query == "recovered" && model.state.value.error == null }
        assertEquals(1, runBlocking { memories.observeMemories().first().size })
    }

    private fun renderHub() {
        val preferences = object : Storage {
            private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
            override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
            override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
            override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
            override fun putInt(key: String, value: Int) { values[key] = value }
            override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
            override fun putBoolean(key: String, value: Boolean) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
            override fun clear() { values.clear() }
        }
        val notes = NotebookUseCases(fixture.notes)
        val conversations = RoomConversationStore(fixture.database, fixture.scope)
        runBlocking { conversations.awaitReady() }
        val search = object : WorkspaceSearchEngine {
            override suspend fun search(query: WorkspaceSearchQuery) = WorkspaceSearchResult(query.query, emptyList())
            override suspend fun indexObject(objectId: com.halo.app.data.notebook.WorkspaceObjectId,
                type: com.halo.app.data.notebook.WorkspaceObjectType, title: String, content: String, updatedAt: Long) = Unit
            override suspend fun removeObject(objectId: com.halo.app.data.notebook.WorkspaceObjectId) = Unit
        }
        val workspace = WorkspaceUseCases(fixture.workspace, search,
            WorkspaceContextStore(fixture.database, fixture.notes, preferences), conversations, fixture.files, memories, notes)
        lateinit var notebook: NotebookViewModel
        compose.runOnUiThread {
            notebook = NotebookViewModel(workspace, notes, NotebookOrganizationRepository(fixture.database.notebookOrganizationDao()), fixture.canvases)
            own("notebook", notebook)
        }
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellHaloOrb provides true, LocalShellPageActions provides actions) {
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
                    MemoryHubScreen(model, notebook, {}, {}, {})
                }
            }
        } }
        await { !model.state.value.loading }
    }
    @Test fun hostedMemoryTabsAreLocalAndInvalidateThePreviousSectionProjection() {
        renderHub()
        compose.onNodeWithText("Memories").assertIsDisplayed()
        val previous = actions.value!!
        compose.onNodeWithText("Suggestions").performClick()
        compose.runOnIdle {
            assertFalse(previous.invoke("page.memory.add"))
            assertTrue(actions.value!!.actions.none { it.id in setOf("page.memory.saved", "page.memory.suggestions", "page.memory.history") })
        }
        compose.onNodeWithText("Learning history").performClick()
        compose.onNodeWithText("Memories").performClick()
        compose.onNodeWithText("Basil needs bright light.").assertIsDisplayed()
    }
    @Test fun searchFailureHasAnExplicitRetryWithoutLosingTheRequestedQuery() {
        renderHub()
        compose.runOnIdle { retriever.unavailable = true }
        compose.onNode(hasSetTextAction()).performTextInput("garden")
        await { model.state.value.error != null }
        compose.runOnIdle { retriever.unavailable = false }
        compose.onNodeWithText("Try again").performClick()
        await { model.state.value.error == null && model.state.value.query == "garden" && !model.state.value.loading }
    }
}
