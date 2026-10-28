package com.halo.app.study

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.study.StudyRepository
import com.halo.app.data.study.StudyVerdict
import com.halo.app.ui.screens.study.*
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
class StudyDeviceRegressionTest {
    private fun fixture(check: suspend (StudyRepository, StudyUseCases, ViewModelStore, MemoryStorage) -> Unit) = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java).allowMainThreadQueries().build()
        val owners = ViewModelStore()
        try {
            val repository = StudyRepository(db, db.studyDao())
            check(repository, StudyUseCases(repository), owners, MemoryStorage())
        } finally { owners.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun childLessonStartsLocallyWithoutGrantAndUsesCloudOnlyWithGrant() = fixture { _, study, owners, storage ->
        val home = StudyHomeViewModel(study, executionStorage = storage)
        owners.put("home", home)
        for (cloudGranted in listOf(false, true)) {
            home.startChildLesson("Why do seasons change?", cloudGranted)
            val id = withTimeout(5000) { home.navigation.first() }
            val session = study.resume(id)!!
            assertNull(home.notice.value)
            assertEquals(StudyLearnerLevel.PRIMARY, session.learnerLevel)
            assertEquals(StudySupportStyle.GUIDED, session.supportStyle)
            assertEquals(StudyKnowledgePolicy.SOURCE_PLUS_GENERAL, session.knowledgePolicy)
            assertEquals(if (cloudGranted) "cloud" else "phone", storage.getString("study_execution_$id"))
            withTimeout(5000) { home.busy.filter { !it }.first() }
        }
    }

    @Test fun discoveryPersistsLearnerWordsWhileTutorReceivesTeachingInstruction() = fixture { repository, study, owners, storage ->
        val session = repository.createSession(emptyList(), null, StudyLanguagePreferences(), "Seasons")
        val lessons = StudyLessonStore(storage)
        var request: TutorTurnRequest? = null
        val gateway = object : StudyTutorGateway {
            override val available = true
            override suspend fun respond(value: TutorTurnRequest): TutorTurnResponse {
                request = value
                return TutorTurnResponse.Ready("**One idea:** Earth's tilt.", value.requestId, emptyList(),
                    StudyVerdict.UNVERIFIED, "", null, "test-model")
            }
        }
        val lesson = StudyViewModel(session.id, study, gateway = gateway, lessons = lessons)
        owners.put("lesson", lesson)
        withTimeout(5000) { lesson.uiState.filter { !it.loading }.first() }
        lesson.discoverPriorKnowledge("I know summer is warmer.")
        val state = withTimeout(5000) { lesson.uiState.filter { it.turns.isNotEmpty() && !it.isGenerating }.first() }
        assertTrue(request!!.learnerQuestion!!.contains("Explain one idea at my level"))
        assertEquals("I know summer is warmer.", state.turns.single().question)
        assertEquals("I know summer is warmer.", lessons.load(session.id).single().question)
        assertEquals(StudyVerdict.UNVERIFIED, state.turns.single().verdict)
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
}
