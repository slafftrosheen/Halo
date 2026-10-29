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
import kotlinx.coroutines.CompletableDeferred
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
    private val actions = mutableStateOf<ShellPageProjection?>(null)
    private val search = ControlledSearchHits()

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
        val workspace = WorkspaceUseCases(fixture.workspace, search,
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

    @Test fun librarySearchPlacesAllMatchingResultsBeforeSecondaryTools() {
        // A tall inspection viewport makes all three real result rows and Tools measurable
        // together. This proves rendered order, not a phone first-screen/scroll-count claim.
        render(height = 1800)
        compose.onNode(hasContentDescription("Search your workspace") and hasSetTextAction())
            .performTextInput(QUERY)
        enterSearch()
        compose.onNode(hasContentDescription("Search your workspace") and hasSetTextAction())
            .assertTextContains(QUERY)
        compose.runOnIdle { model.toggleSelection(resultIds.getValue(NOTE_TITLE)) }
        compose.onNodeWithText("Tools").assertDoesNotExist()
        resultIds.keys.forEach { title ->
            compose.onNodeWithText(title).assertIsDisplayed()
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
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.notebook.study")) }
        assertEquals(1, studyOpens)
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.notebook.memory")) }
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

    @Test fun libraryLandingAndSuiteNavigationPreserveSearchAndRejectStaleActions() {
        render(height = 1800)
        enterSearch()
        compose.onNodeWithText("Open Library").assertDoesNotExist()
        compose.onNodeWithText("Continue").assertDoesNotExist()
        lateinit var root: ShellPageProjection
        compose.runOnIdle { root = actions.value!!; assertTrue(root.invoke("page.notebook.office")) }
        compose.runOnIdle {
            assertFalse(root.invoke("page.notebook.new-note"))
            assertTrue(actions.value!!.actions.any { it.id == "page.notebook.import" })
            assertTrue("halo_library_set_view" in actions.value!!.owner.canonicalToolIds)
            assertEquals("Library", actions.value!!.actions.single { it.id == "page.notebook.suites" }.title)
            assertTrue(actions.value!!.invoke("page.notebook.suites"))
        }
        compose.runOnIdle { assertTrue(actions.value!!.invoke("page.notebook.studio")) }
        compose.runOnIdle {
            assertTrue(actions.value!!.actions.any { it.id == "page.notebook.new-canvas" })
            assertTrue("halo_library_organize" in actions.value!!.owner.canonicalToolIds)
            assertEquals("Library", actions.value!!.actions.single { it.id == "page.notebook.suites" }.title)
            assertTrue(actions.value!!.invoke("page.notebook.suites"))
        }
        assertEquals(QUERY, model.uiState.value.query)
        assertEquals(NotebookSort.TITLE, model.uiState.value.sort)
        assertTrue(navigation.isEmpty())
        assertEquals(0, studyOpens)
    }

    private fun render(height: Int) {
        val preferences = AppLanguagePreferences(MemoryStorage(), "en")
        compose.setContent {
            AppLanguageProvider(preferences) {
                CompositionLocalProvider(LocalShellHaloOrb provides true, LocalShellPageActions provides actions) {
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

    @Test fun semanticViewUsesRealOwnerAndRejectsStaleEntrySelectionAndReplay() {
        render(height = 1800)
        val session = NotebookLibrarySession { true }
        var first: NotebookLibrarySession.Lease? = null
        compose.runOnIdle {
            first = session.attach("library-A", model)
            val before = requireNotNull(session.read())
            val selected = setOf(resultIds.getValue(NOTE_TITLE))
            val result = requireNotNull(session.set(before.snapshot, NotebookLibraryChange.Selection(selected)))
            assertEquals(selected, model.selectedIds.value)
            assertEquals(selected, result.view.selection)
            assertNull(session.set(before.snapshot, NotebookLibraryChange.Selection(emptySet())))
            model.clearSelection()
            assertNull(session.set(result.snapshot, NotebookLibraryChange.Query("wrong stale request")))
            val fresh = requireNotNull(session.read())
            assertNull(session.set(fresh.snapshot, NotebookLibraryChange.Selection(setOf("missing-object"))))
            val replacement = session.attach("library-B", model)
            session.detach(requireNotNull(first))
            assertNotNull(session.read())
            assertNull(session.read(fresh.snapshot))
            session.detach(replacement)
            assertNull(session.read())
        }
    }

    @Test fun typedLibraryToolChangesActualQueryAndReportsPendingInsteadOfOldHitsAsFresh() {
        render(height = 1800)
        val pendingSearch = search.hold(QUERY)
        val session = NotebookLibrarySession { true }
        val tool = com.halo.app.di.tools.host.NotebookLibraryTools(session)
        compose.runOnIdle { session.attach("library", model) }
        // isLoaded alone is not the SET admission contract: the requested and
        // rendered view must agree, and the initial Room projection must exist.
        compose.waitUntil(5_000) {
            scheduler.runCurrent()
            var ready = false
            compose.runOnIdle {
                val frame = session.read()
                ready = frame != null && frame.view.settled && !frame.view.mutating &&
                    frame.view.rendered.items.map { it.objectRef.id.value }.toSet()
                        .containsAll(resultIds.values)
            }
            ready
        }
        compose.runOnIdle {
            val before = requireNotNull(session.read())
            assertTrue("Initial Library lease must be settled", before.view.settled)
            assertFalse("Initial Library lease must not be mutating", before.view.mutating)
            val token = before.snapshot
            runBlocking(com.halo.runtime.cognition.tools.RunToolScope(com.halo.runtime.cognition.agent.AgentRunId("library-test"), targetIsLocal = true)) {
                val result = tool.execute(com.halo.sdk.provider.ToolCall("set", com.halo.app.di.tools.host.NotebookLibraryTools.SET,
                    mapOf("expectedSnapshot" to token, "query" to QUERY)))
                val after = session.read()
                // No tokens, object IDs, titles, snippets or query text in failure diagnostics.
                val diagnostic = "result=${result::class.simpleName}" +
                    ((result as? com.halo.runtime.cognition.tools.ToolExecutionResult.Rejected)?.let {
                        " kind=${it.kind} reason=${it.reason}"
                    } ?: "") +
                    " beforeSettled=${before.view.settled} beforeMutating=${before.view.mutating}" +
                    " afterPresent=${after != null} afterSettled=${after?.view?.settled}" +
                    " afterMutating=${after?.view?.mutating} snapshotUnchanged=${after?.snapshot == token}" +
                    " ownerRevisionUnchanged=${after?.view?.ownerRevision == before.view.ownerRevision}" +
                    " requestedQueryApplied=${model.queryText.value == QUERY}" +
                    " requestedQueryRendered=${model.uiState.value.resolvedQuery == QUERY}"
                assertTrue(diagnostic, result is com.halo.runtime.cognition.tools.ToolExecutionResult.Success)
                val json = org.json.JSONObject((result as com.halo.runtime.cognition.tools.ToolExecutionResult.Success).output)
                assertFalse(json.getBoolean("settled"))
                assertEquals(QUERY, json.getJSONObject("requested").getString("query"))
                assertEquals(QUERY, model.queryText.value)
                assertFalse(json.toString().contains(NOTE_TITLE))
            }
        }
        compose.waitUntil(5_000) { scheduler.runCurrent(); pendingSearch.started.isCompleted }
        compose.runOnIdle { assertFalse(requireNotNull(session.read()).view.settled) }
        pendingSearch.release.complete(Unit)
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.resolvedQuery == QUERY }
        compose.runOnIdle {
            assertTrue(requireNotNull(session.read()).view.settled)
            assertEquals(resultIds.values.toSet(), model.uiState.value.items.map { it.objectRef.id.value }.toSet())
        }
    }

    @Test fun ownedSetReadbackSurvivesRenderingButRejectsACompetingUserEdit() {
        render(height = 1800)
        val pendingSearch = search.hold(QUERY)
        val session = NotebookLibrarySession { true }
        lateinit var pending: NotebookLibraryFrame
        lateinit var readback: NotebookLibrarySession.ReadbackLease
        compose.runOnIdle {
            session.attach("owned-readback", model)
            val initial = requireNotNull(session.read())
            assertTrue(initial.view.settled)
            pending = requireNotNull(session.set(initial.snapshot, NotebookLibraryChange.Query(QUERY)))
            assertFalse(pending.view.settled)
            readback = requireNotNull(session.readbackLease(pending.view.ownerRevision))
            assertNull(session.set(initial.snapshot, NotebookLibraryChange.Query("replayed request")))
        }
        compose.waitUntil(5_000) { scheduler.runCurrent(); pendingSearch.started.isCompleted }
        compose.runOnIdle { assertTrue(session.canReadback(readback)) }
        pendingSearch.release.complete(Unit)
        compose.waitUntil(5_000) { scheduler.runCurrent(); model.uiState.value.resolvedQuery == QUERY }
        compose.runOnIdle {
            assertTrue(requireNotNull(session.read()).view.settled)
            assertNull("An old READ snapshot must remain strict", session.read(pending.snapshot))
            assertTrue("Own asynchronous rendering must not revoke SET acknowledgment", session.canReadback(readback))
            model.setQuery("competing user edit")
            assertFalse(session.canReadback(readback))
            assertNull("Cannot acquire a later edit's lease for an earlier receipt",
                session.readbackLease(pending.view.ownerRevision))
        }
    }

    @Test fun ownedReadbackRejectsRevocationDetachAndReplacementOfTheSameEntryId() {
        render(height = 1800)
        var allowed = true
        val session = NotebookLibrarySession { allowed }
        compose.runOnIdle {
            val entry = session.attach("entry", model)
            val readback = requireNotNull(session.readbackLease())
            allowed = false
            assertFalse(session.canReadback(readback))
            assertNull(session.readbackLease())
            allowed = true
            session.detach(entry)
            assertFalse(session.canReadback(readback))
            session.attach("entry", model)
            assertFalse(session.canReadback(readback))
            assertTrue(session.canReadback(session.readbackLease()))
        }
    }

    @Test fun organizationSessionUsesExistingRoomOwnerAndRejectsReplayAndRevocation() {
        render(height = 1800)
        var allowed = true
        val session = NotebookLibrarySession { allowed }
        val id = resultIds.getValue(NOTE_TITLE)
        compose.runOnIdle {
            session.attach("library", model)
            runBlocking {
                val frame = requireNotNull(session.read())
                val ticket = requireNotNull(session.organization(frame.snapshot, id, null))
                val receipt = session.organize(frame.snapshot, ticket.token, com.halo.app.data.notebook.NotebookOrganizationChange.Favorite(true), objectId = id)
                assertEquals(true, requireNotNull(receipt).value)
                assertTrue(fixture.database.notebookOrganizationDao().isFavorite(id))
                assertNull(session.organize(frame.snapshot, ticket.token, com.halo.app.data.notebook.NotebookOrganizationChange.Favorite(false), objectId = id))
            }
        }
        // Wait for the actual Room-backed VM projection before acquiring another view lease.
        // The preceding write may emit favorites while captureGuarded suspends; rejecting that
        // old snapshot is correct production behavior, not a reason to relax admission.
        compose.waitUntil(5_000) { scheduler.runCurrent(); id in model.uiState.value.favorites }
        compose.runOnIdle {
            runBlocking {
                val fresh = requireNotNull(session.read())
                val next = requireNotNull(session.organization(fresh.snapshot, id, null))
                allowed = false
                assertNull(session.organize(fresh.snapshot, next.token, com.halo.app.data.notebook.NotebookOrganizationChange.Favorite(false), objectId = id))
                assertTrue(fixture.database.notebookOrganizationDao().isFavorite(id))
            }
        }
    }

    @Test fun actualOrganizationEffectWithRevokedReadbackReturnsContentFreeUnknown() {
        render(height = 1800)
        var enforceRevocation = false
        var checks = 0
        val session = NotebookLibrarySession { !enforceRevocation || ++checks <= 3 }
        val tool = com.halo.app.di.tools.host.NotebookOrganizationTools(session)
        val id = resultIds.getValue(NOTE_TITLE)
        compose.runOnIdle {
            session.attach("library", model)
            runBlocking(com.halo.runtime.cognition.tools.RunToolScope(com.halo.runtime.cognition.agent.AgentRunId("library-revocation"), targetIsLocal = true)) {
                val frame = requireNotNull(session.read())
                val ticket = requireNotNull(session.organization(frame.snapshot, id, null))
                enforceRevocation = true
                val call = com.halo.sdk.provider.ToolCall("write", com.halo.app.di.tools.host.NotebookOrganizationTools.WRITE,
                    mapOf("expectedSnapshot" to frame.snapshot, "expectedOrganization" to ticket.token,
                        "operation" to "set_favorite", "objectId" to id, "value" to true))
                val result = tool.execute(call, com.halo.runtime.cognition.tools.ToolExecutionContext(interactionId = "approved-library-interaction"))
                assertTrue(result is com.halo.runtime.cognition.tools.ToolExecutionResult.Failed)
                assertTrue((result as com.halo.runtime.cognition.tools.ToolExecutionResult.Failed).completionUnknown)
                assertFalse(result.toString().contains(id))
                assertTrue(fixture.database.notebookOrganizationDao().isFavorite(id))
            }
        }
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
    private class ControlledSearchHits : WorkspaceSearchEngine {
        class Gate(val query: String) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
        }
        @Volatile private var held: Gate? = null
        fun hold(query: String) = Gate(query).also { held = it }
        override suspend fun search(query: WorkspaceSearchQuery): WorkspaceSearchResult {
            held?.takeIf { it.query == query.query }?.let {
                it.started.complete(Unit)
                it.release.await()
            }
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
        private const val QUERY = "BOARD-D"
        private const val NOTE_TITLE = "BOARD-D alpha note"
        private const val CANVAS_TITLE = "BOARD-D beta canvas"
        private const val SECOND_NOTE_TITLE = "BOARD-D gamma note"
        private const val UNRELATED_TITLE = "Unrelated layout fixture"
    }
}
