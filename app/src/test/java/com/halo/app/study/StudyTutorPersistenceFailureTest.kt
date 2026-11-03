package com.halo.app.study

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.study.StudyRepository
import com.halo.app.data.study.StudyVerdict
import com.halo.app.ui.screens.study.StudyLessonStore
import com.halo.app.ui.screens.study.StudyViewModel
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@OptIn(ExperimentalCoroutinesApi::class)
class StudyTutorPersistenceFailureTest {
    private class ReplyDroppingStorage : Storage {
        private val values = ConcurrentHashMap<String, Any>()
        @Volatile var dropReplies = false
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) {
            if (dropReplies && key.startsWith("study_lesson_v1_")) return
            if (value == null) values.remove(key) else values[key] = value
        }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }

    private fun tutor(onRequest: () -> Unit) = object : StudyTutorGateway {
        override val available = true
        override suspend fun respond(request: TutorTurnRequest): TutorTurnResponse {
            onRequest()
            return TutorTurnResponse.Ready(
                text = "A short unverified explanation", generationId = request.requestId,
                citations = emptyList(), verdict = StudyVerdict.UNVERIFIED,
                nextStep = "Try an example", uncertainty = "Not independently verified", modelId = "fixture"
            )
        }
    }

    @Test fun droppedResponseWriteKeepsDraftAndRejectsUnsavedLearningProgress() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val owner = ViewModelStore()
        val jobs = mutableListOf<Job>()
        try {
            val repository = StudyRepository(db, db.studyDao())
            val session = repository.createSession(emptyList(), null, StudyLanguagePreferences(), "Vocabulary")
            val storage = ReplyDroppingStorage()
            val lessons = StudyLessonStore(storage)
            var requests = 0
            val vm = StudyViewModel(session.id, StudyUseCases(repository), gateway = tutor { requests++ }, lessons = lessons)
            owner.put("lesson", vm)
            jobs += vm.viewModelScope.coroutineContext.job
            withTimeout(5000) { vm.uiState.first { !it.loading } }
            vm.setDraft("Explain this term")
            withTimeout(5000) { vm.uiState.first { it.draftKept && it.draft == "Explain this term" } }
            storage.dropReplies = true
            vm.askQuestion()
            withTimeout(5000) { vm.uiState.first { !it.isGenerating && it.notice.orEmpty().contains("not saved") } }
            assertEquals(1, requests)
            assertTrue(vm.uiState.value.turns.isEmpty())
            assertEquals("Explain this term", vm.uiState.value.draft)
            assertTrue(vm.uiState.value.draftKept)
            assertEquals("Explain this term", lessons.loadRecovery(session.id).draft)
            assertTrue(lessons.load(session.id).isEmpty())
        } finally {
            owner.clear()
            try { withTimeout(5000) { jobs.joinAll() } }
            finally { db.close(); Dispatchers.resetMain() }
        }
    }

    @Test fun confirmedResponseStillCommitsAndClearsOnlySubmittedDraft() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val owner = ViewModelStore()
        val jobs = mutableListOf<Job>()
        try {
            val repository = StudyRepository(db, db.studyDao())
            val session = repository.createSession(emptyList(), null, StudyLanguagePreferences(), "Vocabulary")
            val lessons = StudyLessonStore(ReplyDroppingStorage())
            val vm = StudyViewModel(session.id, StudyUseCases(repository), gateway = tutor {}, lessons = lessons)
            owner.put("lesson", vm)
            jobs += vm.viewModelScope.coroutineContext.job
            withTimeout(5000) { vm.uiState.first { !it.loading } }
            vm.setDraft("Explain this term")
            withTimeout(5000) { vm.uiState.first { it.draftKept } }
            vm.askQuestion()
            withTimeout(5000) { vm.uiState.first { it.turns.size == 1 && !it.isGenerating } }
            assertEquals("", vm.uiState.value.draft)
            assertEquals("A short unverified explanation", lessons.load(session.id).single().text)
            assertEquals("Explain this term", lessons.load(session.id).single().question)
        } finally {
            owner.clear()
            try { withTimeout(5000) { jobs.joinAll() } }
            finally { db.close(); Dispatchers.resetMain() }
        }
    }
}
