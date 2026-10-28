package com.halo.app.study

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.study.StudyRepository
import com.halo.app.ui.screens.study.StudyViewModel
import com.halo.app.ui.screens.study.StudyLessonStore
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE,application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@OptIn(ExperimentalCoroutinesApi::class)
class StudyReaderRecoveryTest {
    @Test fun explicitKnowledgeSkipAndDepthPreserveDraftAndRestoreWithoutTutorOrGrading() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            for (priorKnowledge in listOf("", "My earlier knowledge stays mine")) {
                val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
                    AppDatabase::class.java).allowMainThreadQueries().build()
                val owners = ViewModelStore()
                val jobs = mutableListOf<Job>()
                try {
                    val repository = StudyRepository(db, db.studyDao())
                    val session = repository.createSession(emptyList(), null, StudyLanguagePreferences(), "Fractions")
                    val storage = MemoryStorage()
                    val lessons = StudyLessonStore(storage)
                    lessons.saveDraft(session.id, "My unsent thinking")
                    lessons.saveJourney(session.id, com.halo.app.ui.screens.study.LearningJourney(priorKnowledge = priorKnowledge))
                    var requests = 0
                    val gateway = object : StudyTutorGateway {
                        override val available = true
                        override suspend fun respond(request: TutorTurnRequest): TutorTurnResponse {
                            requests++
                            error("Skipping the probe must not start inference")
                        }
                    }
                    val useCases = StudyUseCases(repository)
                    val vm = StudyViewModel(session.id, useCases, gateway = gateway, lessons = lessons)
                    owners.put("lesson", vm)
                    jobs += vm.viewModelScope.coroutineContext.job
                    withTimeout(5000) { vm.uiState.first { !it.loading } }
                    val before = repository.getSession(session.id)
                    vm.setExplanationDepth(StudyExplanationDepth.DEEPER)
                    withTimeout(5000) { vm.uiState.first { it.journey.explanationDepth == StudyExplanationDepth.DEEPER && !it.isGenerating } }
                    vm.skipKnowledgeProbe()
                    withTimeout(5000) { vm.uiState.first { it.journey.probeSkipped && !it.isGenerating } }
                    assertEquals(priorKnowledge, vm.uiState.value.journey.priorKnowledge)
                    assertEquals("My unsent thinking", vm.uiState.value.draft)
                    assertTrue(repository.getAttempts(session.id).isEmpty())
                    assertEquals(before, repository.getSession(session.id))
                    assertTrue(lessons.load(session.id).isEmpty())
                    clearAndAwait(owners, jobs)
                    val restored = StudyViewModel(session.id, useCases, gateway = gateway, lessons = StudyLessonStore(storage))
                    owners.put("restored", restored)
                    jobs += restored.viewModelScope.coroutineContext.job
                    withTimeout(5000) { restored.uiState.first { !it.loading && it.journey.probeSkipped } }
                    assertEquals("My unsent thinking", restored.uiState.value.draft)
                    assertEquals(priorKnowledge, restored.uiState.value.journey.priorKnowledge)
                    assertEquals(StudyExplanationDepth.DEEPER, restored.uiState.value.journey.explanationDepth)
                    assertEquals(0, requests)
                } finally { try { clearAndAwait(owners, jobs) } finally { db.close() } }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun interruptedGenerationRestoresDraftWithoutReplayingTheTutor() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java).allowMainThreadQueries().build()
        val owners = ViewModelStore()
        val jobs = mutableListOf<Job>()
        try {
            val repository = StudyRepository(db, db.studyDao())
            val session = repository.createSession(emptyList(), null, StudyLanguagePreferences("lv", "ru", "en"), "Fractions")
            val lessons = StudyLessonStore(MemoryStorage())
            lessons.saveDraft(session.id, "My unfinished question")
            lessons.markPendingTurn(session.id, "interrupted-request")
            var tutorRequests = 0
            val gateway = object : StudyTutorGateway {
                override val available = true
                override suspend fun respond(request: TutorTurnRequest): TutorTurnResponse {
                    tutorRequests++
                    error("Restore must never replay generation")
                }
            }
            val vm = StudyViewModel(session.id, StudyUseCases(repository), gateway = gateway, lessons = lessons)
            owners.put("restored", vm)
            jobs += vm.viewModelScope.coroutineContext.job
            withTimeout(5000) { vm.uiState.filter { !it.loading }.first() }
            assertEquals("My unfinished question", vm.uiState.value.draft)
            assertTrue(vm.uiState.value.notice.orEmpty().contains("interrupted"))
            assertFalse(vm.uiState.value.isGenerating)
            assertEquals("ru", vm.uiState.value.session!!.languages.explanationLanguage)
            assertEquals(0, tutorRequests)
        } finally { try { clearAndAwait(owners, jobs) } finally { db.close(); Dispatchers.resetMain() } }
    }

    // Cancellation is asynchronous when Room resumes an IO operation. Drain the owned
    // scopes before closing their database or replacing the process-wide Main dispatcher.
    private suspend fun clearAndAwait(store: ViewModelStore, jobs: List<Job>) {
        store.clear()
        withTimeout(5000) { jobs.joinAll() }
    }
    private class MemoryStorage : Storage {
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
    private class Narrator : StudyNarrationController {
        override val available=true
        val receipts=MutableSharedFlow<NarrationEvent>(extraBufferCapacity=16)
        override val events=receipts.asSharedFlow()
        var last=""
        override fun narrate(text: String,sessionId: String,generationId: String) {
            last=generationId
            // A cached/local narrator may acknowledge playback before narrate returns.
            check(receipts.tryEmit(NarrationEvent.Started(generationId)))
        }
        override fun stop(reason: String)=Unit
    }
    @Test fun pauseKeepsPositionAndLateCompletionCannotAdvanceResumedReader() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        val store=ViewModelStore()
        val jobs=mutableListOf<Job>()
        try {
            val repository=StudyRepository(db,db.studyDao())
            val session=repository.createSession(listOf("source"),7,StudyLanguagePreferences(),"Learn")
            val narrator=Narrator()
            val resolver=StudySourceResolver { _,_ -> ResolvedStudySource.Text("source","7","Source",
                listOf(StudyPassage("First. Second.",StudySourceLocator.NoteBlocks(1,1))),14,SourceCoverage.FULL) }
            val vm=StudyViewModel(session.id,StudyUseCases(repository,narration=narrator,sourceResolver=resolver))
            store.put("reader",vm)
            jobs += vm.viewModelScope.coroutineContext.job
            withTimeout(5000) { vm.uiState.filter { !it.loading && it.sources.isNotEmpty() }.first() }
            vm.readSourceAloud()
            withTimeout(5000) { vm.uiState.first { it.reader?.speaking == true } }
            val first=narrator.last
            assertTrue(vm.uiState.value.reader!!.speaking)
            vm.toggleReaderPlayback()
            assertNotNull(vm.uiState.value.reader)
            assertFalse(vm.uiState.value.reader!!.speaking)
            narrator.receipts.emit(NarrationEvent.Completed(first))
            assertEquals(0,vm.uiState.value.reader!!.index)
            vm.toggleReaderPlayback()
            val resumed=narrator.last
            assertNotEquals(first,resumed)
            narrator.receipts.emit(NarrationEvent.Completed(first))
            assertEquals(0,vm.uiState.value.reader!!.index)
            narrator.receipts.emit(NarrationEvent.Range(first,0,3))
            assertNull(vm.uiState.value.reader!!.wordRange)
            narrator.receipts.emit(NarrationEvent.Range(resumed,0,3))
            assertEquals(0..2,vm.uiState.value.reader!!.wordRange)
            narrator.receipts.emit(NarrationEvent.Range(resumed,0,999))
            assertEquals(0..2,vm.uiState.value.reader!!.wordRange)
            narrator.receipts.emit(NarrationEvent.Completed(resumed))
            assertEquals(1,vm.uiState.value.reader!!.index)
            vm.closeReader()
            assertNull(vm.uiState.value.reader)
        } finally { try { clearAndAwait(store, jobs) } finally { db.close(); Dispatchers.resetMain() } }
    }
}
