package com.halo.app.ui.screens.study

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.halo.app.data.study.*
import com.halo.app.data.workspace.WorkspaceFileToolsFixture
import com.halo.app.study.*
import com.halo.sdk.runtime.study.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class StudySetupRecoveryTest {
    @Test fun unavailableMaterialExplainsItsActualRecovery() {
        assertEquals(com.halo.app.R.string.study_read_loading,
            studyMaterialUnavailableMessage(StudyHomeReadState(loading = setOf("preview"))))
        assertEquals(com.halo.app.R.string.study_read_retry_material,
            studyMaterialUnavailableMessage(StudyHomeReadState(loading = emptySet(), failed = setOf("preview"))))
        assertEquals(com.halo.app.R.string.study_selection_restore_failed,
            studyMaterialUnavailableMessage(StudyHomeReadState(selectionRestoreFailed = true)))
        assertEquals(com.halo.app.R.string.study_material_changed,
            studyMaterialUnavailableMessage(StudyHomeReadState(loading = emptySet())))
    }
    private lateinit var main: ExecutorCoroutineDispatcher
    private lateinit var fixture: WorkspaceFileToolsFixture
    private val owners = ViewModelStore()
    private val jobs = mutableListOf<Job>()
    @Before fun setup() {
        main = Executors.newSingleThreadExecutor().asCoroutineDispatcher(); Dispatchers.setMain(main)
        fixture = WorkspaceFileToolsFixture()
    }
    @After fun close() {
        runBlocking(main) { owners.clear(); jobs.joinAll() }
        fixture.close(); Dispatchers.resetMain(); main.close()
    }
    private fun own(model: StudyHomeViewModel): StudyHomeViewModel {
        owners.put(jobs.size.toString(), model); jobs += requireNotNull(model.viewModelScope.coroutineContext[Job]); return model
    }
    private fun restored(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
    private fun resolver(revision: () -> String = { "1" }) = StudySourceResolver { id, _ ->
        ResolvedStudySource.Text(id, revision(), "Fixture material", listOf(StudyPassage("Body", StudySourceLocator.TextRange(0, 4))), 4, SourceCoverage.FULL)
    }
    private fun study(dao: StudyDao = fixture.database.studyDao()) = StudyUseCases(StudyRepository(fixture.database, dao))
    @Test fun removedRouteSeedAndManualReplacementSurviveFreshOwnerAndReset() = runBlocking(main) {
        val handle = SavedStateHandle(); val home = own(StudyHomeViewModel(study(), resolver = resolver(), savedState = handle))
        home.selectInitialSource("route-A")
        withTimeout(5000) { home.previews.first { it.size == 1 } }
        home.remove("route-A"); home.select("manual-B"); home.select("manual-C")
        withTimeout(5000) { home.previews.first { it.size == 2 } }
        val nextHandle = restored(handle)
        val next = own(StudyHomeViewModel(study(), resolver = resolver(), savedState = nextHandle))
        next.selectInitialSource("route-A")
        assertEquals(listOf("manual-B", "manual-C"), next.selectedIds.value)
        withTimeout(5000) { next.previews.first { it.size == 2 } }
        assertTrue(next.reads.value.materialReady)
        next.resetDraft()
        val empty = own(StudyHomeViewModel(study(), resolver = resolver(), savedState = restored(nextHandle)))
        empty.selectInitialSource("route-A")
        assertTrue(empty.selectedIds.value.isEmpty())
    }
    @Test fun exactAnchorAndVersionAreRevalidatedInsteadOfSilentlyBroadening() = runBlocking(main) {
        val handle = SavedStateHandle()
        val anchor = StudyTextAnchor("block-1", 2, 8)
        var version = "v1"
        var seen: StudyTextAnchor? = null
        val anchored = object : StudySourceResolver {
            override suspend fun resolve(objectId: String, query: String): ResolvedStudySource = error("Must not broaden anchor")
            override suspend fun resolveTextAnchor(objectId: String, revision: String, anchor: StudyTextAnchor): ResolvedStudySource {
                seen = anchor
                return ResolvedStudySource.Text(objectId, version, "Exact passage", listOf(StudyPassage("Copper", StudySourceLocator.NoteSelection(anchor.blockId, anchor.start, anchor.end))), 6, SourceCoverage.FULL)
            }
        }
        val first = own(StudyHomeViewModel(study(), resolver = anchored, savedState = handle))
        first.select("source", sourceReference = StudySessionSource("source", "v1", anchor))
        withTimeout(5000) { first.previews.first { it.isNotEmpty() } }
        val next = own(StudyHomeViewModel(study(), resolver = anchored, savedState = restored(handle)))
        withTimeout(5000) { next.previews.first { it.isNotEmpty() } }
        assertEquals(anchor, seen)
        version = "v2"; next.retryReads()
        withTimeout(5000) { next.previews.first { it.firstOrNull() is ResolvedStudySource.Unsupported } }
        next.start("Retained topic", "en", executionChoice = "phone")
        assertFalse(next.busy.value)
        assertEquals(StudyUnavailableReason.SOURCE_CHANGED.explanation, next.notice.value)
        assertTrue(fixture.database.studyDao().observeActiveSessions().first().isEmpty())
    }
    @Test fun failedPreviewRetainsSelectionAndRetriesWithoutStaleResult() = runBlocking(main) {
        var fail = true
        val gate = CompletableDeferred<Unit>()
        val source = StudySourceResolver { id, _ ->
            if (fail) throw java.io.IOException("Synthetic source failure")
            if (id == "older") gate.await()
            resolver().resolve(id, "")
        }
        val home = own(StudyHomeViewModel(study(), resolver = source))
        home.select("older")
        withTimeout(5000) { home.reads.first { "preview" in it.failed } }
        assertEquals(listOf("older"), home.selectedIds.value)
        home.start("Retained topic", "en", executionChoice = "phone")
        assertFalse(home.busy.value)
        fail = false; home.retryReads()
        home.remove("older"); home.select("newer"); gate.complete(Unit)
        withTimeout(5000) { home.previews.first { it.singleOrNull()?.objectId == "newer" } }
        assertEquals(listOf("newer"), home.selectedIds.value)
        assertTrue(home.reads.value.materialReady)
    }
    @Test fun failedSessionReadIsNotEmptySuccessAndRetryRetainsLastLoadedContent() = runBlocking(main) {
        val real = fixture.database.studyDao(); var fail = true
        val dao = object : StudyDao by real {
            override fun observeActiveSessions(): Flow<List<StudySessionEntity>> = flow {
                if (fail) throw java.io.IOException("Synthetic read failure")
                emitAll(real.observeActiveSessions())
            }
        }
        val home = own(StudyHomeViewModel(study(dao), resolver = resolver()))
        val collecting = launch { home.sessions.collect() }
        try {
            withTimeout(5000) { home.reads.first { "sessions" in it.failed } }
            assertFalse(home.reads.value.libraryReady)
            fail = false; home.retryReads()
            withTimeout(5000) { home.reads.first { it.libraryReady } }
            assertTrue(home.sessions.value.isEmpty())
            val created = StudyRepository(fixture.database, real).createSession(emptyList(), null, StudyLanguagePreferences(), "Retained lesson")
            withTimeout(5000) { home.sessions.first { it.any { it.id == created.id } } }
            fail = true; home.retryReads()
            withTimeout(5000) { home.reads.first { "sessions" in it.failed } }
            assertEquals(created.id, home.sessions.value.single().id)
        } finally { collecting.cancelAndJoin() }
    }
    @Test fun malformedRecoveryOrExactSeedCannotStartGeneralLesson() = runBlocking(main) {
        for (malformedSaved in listOf(true, false)) {
            val handle = if (malformedSaved) SavedStateHandle(mapOf("study_material_selection" to "{broken")) else SavedStateHandle()
            val home = own(StudyHomeViewModel(study(), resolver = resolver(), savedState = handle))
            if (!malformedSaved) home.selectInitialSource("source", exactReferenceRequired = true)
            home.start("Retained topic", "en", executionChoice = "phone")
            assertFalse(home.busy.value)
            assertTrue(home.reads.value.selectionRestoreFailed)
            assertTrue(fixture.database.studyDao().observeActiveSessions().first().isEmpty())
        }
    }
}
