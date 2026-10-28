package com.halo.app.study

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.study.StudyRepository
import com.halo.app.data.study.StudyVerdict
import com.halo.app.ui.screens.study.StudyViewModel
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveStudyToolsOwnerTest {
    @Test fun explicitSkipUsesSameOwnerPreservesDraftAndConsumesItsExactToken() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val store = ViewModelStore()
        try {
            val repository = StudyRepository(db, db.studyDao())
            val session = repository.createSession(emptyList(), null, StudyLanguagePreferences(), "Fractions")
            val vm = StudyViewModel(session.id, StudyUseCases(repository))
            store.put("lesson", vm)
            withTimeout(5000) { vm.uiState.first { !it.loading } }
            vm.setDraft("My unfinished thinking")
            val owner = ActiveStudyToolsOwner(); owner.attach(vm)
            val before = ready(owner)
            assertTrue(runCatching { owner.dispatch(before.sessionId, before.sourceVersion, ActiveStudyCommand("skip_probe")) { false } }.isFailure)
            assertFalse(vm.uiState.value.journey.probeSkipped)
            owner.dispatch(before.sessionId, before.sourceVersion, ActiveStudyCommand("skip_probe")) { true }
            withTimeout(5000) { vm.uiState.first { it.journey.probeSkipped && !it.isGenerating } }
            val after = withTimeout(5000) {
                var snapshot = ready(owner)
                while (snapshot.busy) { delay(10); snapshot = ready(owner) }
                snapshot
            }
            assertTrue(after.knowledgeProbeSkipped)
            assertTrue(after.hasDraft)
            assertEquals("My unfinished thinking", vm.uiState.value.draft)
            assertEquals("", vm.uiState.value.journey.priorKnowledge)
            assertTrue(repository.getAttempts(session.id).isEmpty())
            assertTrue(runCatching { owner.dispatch(before.sessionId, before.sourceVersion, ActiveStudyCommand("skip_probe")) { true } }.isFailure)
            owner.dispatch(after.sessionId, after.sourceVersion, ActiveStudyCommand("explanation_depth", depth = StudyExplanationDepth.SIMPLER.name)) { true }
            withTimeout(5000) { vm.uiState.first { it.journey.explanationDepth == StudyExplanationDepth.SIMPLER && !it.isGenerating } }
            assertEquals(StudyExplanationDepth.SIMPLER.name, ready(owner).explanationDepth)
            assertEquals("My unfinished thinking", vm.uiState.value.draft)
            assertTrue(repository.getAttempts(session.id).isEmpty())
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }

    private suspend fun ready(owner: ActiveStudyToolsOwner): ActiveStudySnapshot = withTimeout(5000) {
        var snapshot: ActiveStudySnapshot? = null
        while (snapshot == null) {
            snapshot = runCatching { owner.current() }.getOrNull()
            if (snapshot == null) delay(10)
        }
        requireNotNull(snapshot)
    }
    @Test fun firstCanonicalWriteRejectsAChangedSavedSessionInsideItsTransaction() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = StudyRepository(db, db.studyDao())
            val study = StudyUseCases(repository)
            val original = repository.createSession(emptyList(), null, StudyLanguagePreferences(), "Original goal")
            assertTrue(repository.updatePreferences(original.id, null, null, null, "Changed goal"))
            assertTrue(runCatching { study.submitAttempt(original.id, "literal answer", expectedSession = original,
                expectedVersions = emptyMap()) }.isFailure)
            assertTrue(runCatching { study.moveTo(original.id, com.halo.sdk.runtime.study.TutorStage.HINT,
                guidanceExplicitlyRequested = true, expectedSession = original, expectedVersions = emptyMap()) }.isFailure)
            assertEquals("Changed goal", repository.getSession(original.id)!!.goal)
        } finally { db.close() }
    }
    @Test fun attachmentDraftAndExactViewTokensCannotRouteActionsToAnUnrelatedLesson() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val store = ViewModelStore()
        try {
            val repository = StudyRepository(db, db.studyDao())
            val session = repository.createSession(emptyList(), null, StudyLanguagePreferences(), "Fractions")
            val vm = StudyViewModel(session.id, StudyUseCases(repository))
            store.put("lesson", vm)
            withTimeout(5000) { vm.uiState.first { !it.loading } }
            val owner = ActiveStudyToolsOwner()
            assertTrue(runCatching { owner.current() }.isFailure)
            val attachment = owner.attach(vm)
            val first = ready(owner)
            vm.setDraft("My existing learner answer")
            assertTrue(runCatching { owner.dispatch(first.sessionId, first.sourceVersion, ActiveStudyCommand("advance")) { true } }.isFailure)
            val draft = ready(owner)
            assertTrue(draft.hasDraft)
            assertTrue(runCatching { owner.dispatch(draft.sessionId, draft.sourceVersion,
                ActiveStudyCommand("ask_tutor", text = "replacement")) { true } }.isFailure)
            assertEquals("My existing learner answer", vm.uiState.value.draft)
            val duplicate = owner.attach(vm)
            assertTrue(runCatching { owner.current() }.isFailure)
            owner.detach(duplicate); owner.detach(attachment)
            assertTrue(runCatching { owner.current() }.isFailure)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }
    @Test fun scopedTutorDispatchQueuesTheNativeOwnerAndReadbackWaitsForActualTurn() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val store = ViewModelStore()
        val release = CompletableDeferred<Unit>()
        try {
            val repository = StudyRepository(db, db.studyDao())
            val session = repository.createSession(emptyList(), null, StudyLanguagePreferences(), "Fractions")
            val entered = CompletableDeferred<TutorTurnRequest>()
            val gateway = object : StudyTutorGateway {
                override val available = true
                override suspend fun respond(request: TutorTurnRequest): TutorTurnResponse {
                    entered.complete(request); release.await()
                    return TutorTurnResponse.Ready("A half is one of two equal parts.", request.requestId, emptyList(),
                        StudyVerdict.UNVERIFIED, "", null, "fixture")
                }
            }
            val vm = StudyViewModel(session.id, StudyUseCases(repository), gateway = gateway)
            store.put("lesson", vm)
            withTimeout(5000) { vm.uiState.first { !it.loading } }
            val owner = ActiveStudyToolsOwner(); owner.attach(vm)
            val first = ready(owner)
            val queued = owner.dispatch(first.sessionId, first.sourceVersion, ActiveStudyCommand("ask_tutor", text = "Explain a half")) { true }
            assertEquals("ask_tutor", queued.action)
            val request = withTimeout(5000) { entered.await() }
            assertEquals("Explain a half", request.learnerQuestion)
            assertTrue(ready(owner).busy)
            assertTrue(ready(owner).turnIds.isEmpty())
            assertTrue(runCatching { owner.dispatch(first.sessionId, first.sourceVersion, ActiveStudyCommand("ask_tutor")) { true } }.isFailure)
            release.complete(Unit)
            withTimeout(5000) { vm.uiState.first { it.turns.isNotEmpty() && !it.isGenerating } }
            assertEquals(listOf(request.requestId), ready(owner).turnIds)
            assertEquals("A half is one of two equal parts.", ready(owner).latestTurn)
        } finally { release.complete(Unit); store.clear(); db.close(); Dispatchers.resetMain() }
    }
}
