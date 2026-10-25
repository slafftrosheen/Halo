package com.halo.app.ui.screens.study

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.study.StudyPracticeItem
import com.halo.app.data.study.StudyRepository
import com.halo.app.data.study.StudySessionRecord
import com.halo.app.study.*
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Main-confined VM/Room state, with only source completion ordering controlled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class StudySourceRefreshStateTest {
    private val scheduler = TestCoroutineScheduler()
    private val main = StandardTestDispatcher(scheduler)
    private val commands = CoroutineScope(SupervisorJob() + main)
    private val owners = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var repository: StudyRepository
    private lateinit var useCases: StudyUseCases
    private lateinit var session: StudySessionRecord
    private lateinit var card: StudyPracticeItem
    private lateinit var model: StudyViewModel
    private lateinit var modelJob: Job
    private val resolver = DeferredResolver()

    private data class SourceRead(val id: String, val query: String, val result: CompletableDeferred<ResolvedStudySource>)
    private class DeferredResolver : StudySourceResolver {
        val reads = mutableListOf<SourceRead>()
        override suspend fun resolve(objectId: String, query: String): ResolvedStudySource {
            val read = SourceRead(objectId, query, CompletableDeferred())
            reads += read
            return read.result.await()
        }
    }

    @Before fun setup() {
        Dispatchers.setMain(main)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = StudyRepository(db, db.studyDao())
        useCases = StudyUseCases(repository, sourceResolver = resolver)
        runBlocking {
            session = repository.createSession(listOf("original-source"), 7, StudyLanguagePreferences(), "Synthetic lesson")
            card = repository.addPracticeDraft(session.id, "Synthetic question?", "Synthetic answer", "original-source", 7)
        }
        commands.launch {
            model = StudyViewModel(session.id, useCases)
            modelJob = requireNotNull(model.viewModelScope.coroutineContext[Job])
            owners.put("lesson", model)
        }
        await("initial restore must reach the controlled source read") { resolver.reads.size == 1 }
        assertTrue(model.uiState.value.loading)
        assertEquals(session.id, model.uiState.value.session?.id)
    }

    @After fun close() {
        try {
            commands.launch { owners.clear() }
            scheduler.runCurrent()
            if (::modelJob.isInitialized) await("cancelled VM must finish before Room closes") { modelJob.isCompleted }
        } finally {
            commands.cancel()
            scheduler.runCurrent()
            try { if (::db.isInitialized) db.close() } finally { Dispatchers.resetMain() }
        }
    }

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (true) {
            scheduler.runCurrent()
            if (condition()) return
            if (System.nanoTime() >= deadline) throw AssertionError(label)
            // Real Room workers are not virtual-time tasks. Yield only this test
            // thread while the owned Main queue remains explicitly controlled.
            Thread.sleep(5)
        }
    }

    private fun finishRead(index: Int, revision: String = "7") {
        val read = resolver.reads[index]
        val text = "Synthetic material for ${read.id}."
        assertTrue(read.result.complete(ResolvedStudySource.Text(read.id, revision, "Synthetic source",
            listOf(StudyPassage(text, StudySourceLocator.TextRange(0, text.length))), text.length, SourceCoverage.FULL)))
        scheduler.runCurrent()
    }

    private fun loaded() = await("loaded lesson must retain its real practice/mastery projection") {
        !model.uiState.value.loading && model.uiState.value.practiceItems.singleOrNull()?.id == card.id &&
            model.uiState.value.mastery.containsKey(card.id)
    }

    @Test fun lateResumeRefreshCannotRestoreLoadingOrOverwriteNewerPracticeMasteryAndLearnerDraft() {
        lateinit var refresh: Job
        commands.launch { refresh = model.refreshSources() }
        await("resume refresh must overlap initial loading") { resolver.reads.size == 2 }
        assertTrue(model.uiState.value.loading)
        finishRead(0)
        loaded()
        commands.launch { model.setDraft("New learner draft after initial restore"); model.setCardAnswer(card.id, "New card attempt") }
        scheduler.runCurrent()
        val before = model.uiState.value
        assertEquals(listOf("original-source"), before.sources.map { it.objectId })
        finishRead(1)
        await("resume refresh must complete") { refresh.isCompleted }
        val after = model.uiState.value
        assertFalse("A completed restore must never return to the loading spinner", after.loading)
        assertEquals(before.session, after.session)
        assertEquals(before.practiceItems, after.practiceItems)
        assertEquals(before.mastery, after.mastery)
        assertEquals(before.curves, after.curves)
        assertEquals(before.dueReviewItems, after.dueReviewItems)
        assertEquals(before.draft, after.draft)
        assertEquals(before.cardAnswers, after.cardAnswers)
        assertTrue(runBlocking { repository.getReviewEvents(card.id) }.isEmpty())
    }

    @Test fun oldSourceRefreshCannotReplaceNewlySelectedCanonicalMaterialOrItsSession() {
        finishRead(0)
        loaded()
        lateinit var oldRefresh: Job
        commands.launch { oldRefresh = model.refreshSources() }
        await("old-material refresh must be pending") { resolver.reads.size == 2 }
        val change = commands.async { useCases.setActiveSources(session.id, listOf("replacement-source")) }
        await("canonical source change must resolve its exact replacement") { resolver.reads.size == 3 }
        assertEquals("replacement-source", resolver.reads[2].id)
        finishRead(2, "9")
        await("canonical source change and session observer must finish") {
            change.isCompleted && model.uiState.value.session?.sourceObjectIds == listOf("replacement-source")
        }
        assertTrue(runBlocking { change.await() })
        lateinit var newRefresh: Job
        commands.launch { newRefresh = model.refreshSources() }
        await("new-material refresh must use the updated session") { resolver.reads.size == 4 }
        assertEquals("replacement-source", resolver.reads[3].id)
        finishRead(3, "9")
        await("new source must be projected before stale completion") { newRefresh.isCompleted && model.uiState.value.sources.singleOrNull()?.objectId == "replacement-source" }
        val before = model.uiState.value
        finishRead(1)
        await("old-material refresh must finish or discard safely") { oldRefresh.isCompleted }
        val after = model.uiState.value
        assertEquals("An old refresh must not revert the canonical session", before.session, after.session)
        assertEquals(before.sources, after.sources)
        assertFalse(after.loading)
        assertEquals(listOf("replacement-source"), runBlocking { repository.getSession(session.id)!!.sourceObjectIds })
        assertEquals("9", after.sources.single().version)
        assertEquals(before.practiceItems, after.practiceItems)
        assertEquals(before.mastery, after.mastery)
    }

    @Test fun initialRestoreCannotRevertCanonicalMaterialChangedWhileItsSourceReadWasPending() {
        val change = commands.async { useCases.setActiveSources(session.id, listOf("replacement-source")) }
        await("replacement source must resolve while initial restore is pending") { resolver.reads.size == 2 }
        finishRead(1, "9")
        await("replacement session must be observed") {
            change.isCompleted && model.uiState.value.session?.sourceObjectIds == listOf("replacement-source")
        }
        assertTrue(runBlocking { change.await() })
        lateinit var refresh: Job
        commands.launch { refresh = model.refreshSources() }
        await("replacement refresh must start") { resolver.reads.size == 3 }
        finishRead(2, "9")
        await("replacement refresh must complete") { refresh.isCompleted }
        val before = model.uiState.value
        finishRead(0)
        loaded()
        val after = model.uiState.value
        assertEquals("Initial restore cannot replace a newer canonical session", before.session, after.session)
        assertEquals(before.sources, after.sources)
        assertEquals("9", after.sources.single().version)
    }

    @Test fun suspendedMaterialSearchCannotReplaceNewerLearnerInputWithItsStartingSnapshot() {
        finishRead(0)
        loaded()
        lateinit var search: Job
        commands.launch { search = model.findInMaterial("Synthetic") }
        await("material search must enter controlled source resolution") { resolver.reads.size == 2 }
        assertEquals("Synthetic", resolver.reads[1].query)
        commands.launch { model.setDraft("Draft written while material search was pending"); model.setCardAnswer(card.id, "Attempt written during search") }
        scheduler.runCurrent()
        val before = model.uiState.value
        finishRead(1)
        await("material search must finish") { search.isCompleted }
        val after = model.uiState.value
        assertEquals(before.draft, after.draft)
        assertEquals(before.cardAnswers, after.cardAnswers)
        assertEquals(before.session, after.session)
        assertEquals(before.practiceItems, after.practiceItems)
        assertEquals(before.mastery, after.mastery)
        assertFalse(after.loading)
        assertEquals("Showing matching text passages; PDFs have no text extractor", after.notice)
        assertTrue(runBlocking { repository.getReviewEvents(card.id) }.isEmpty())
    }
}
