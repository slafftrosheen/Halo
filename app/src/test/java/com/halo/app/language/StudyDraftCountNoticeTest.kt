package com.halo.app.language

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.study.StudyRepository
import com.halo.app.data.study.StudyVerdict
import com.halo.app.study.*
import com.halo.app.ui.screens.study.StudyScreen
import com.halo.app.ui.screens.study.StudyViewModel
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual three-card creation, then localized host notice; no provider, grading or scheduling claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class StudyDraftCountNoticeTest {
    @get:Rule val compose = createComposeRule()
    private val owners = ViewModelStore()
    private val vmScheduler = TestCoroutineScheduler()
    private var database: AppDatabase? = null
    @Before fun prepareCoroutineMain() {
        // Room writes suspend the VM independently of Compose's frame clock. Match the
        // existing Canvas UI fixture's explicit VM scheduler instead of relying on frames
        // to resume a transaction continuation on Compose's paused main dispatcher.
        Dispatchers.setMain(UnconfinedTestDispatcher(vmScheduler))
    }
    @After fun close() {
        try {
            compose.runOnIdle { owners.clear() }
            vmScheduler.runCurrent()
        } finally {
            try { database?.close() } finally { Dispatchers.resetMain() }
        }
    }
    @Test fun russianThreeDraftNoticeUsesCatalogueFormatAndPreservesCount() = checkDraftCountNotice("ru")
    @Test fun latvianThreeDraftNoticeUsesCatalogueFormatAndPreservesCount() = checkDraftCountNotice("lv")
    @Test fun polishThreeDraftNoticeUsesCatalogueFormatAndPreservesCount() = checkDraftCountNotice("pl")
    private fun checkDraftCountNotice(tag: String) {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build().also { database = it }
        val repository = StudyRepository(db, db.studyDao())
        val session = runBlocking { repository.createSession(listOf("literal-source"), 7, StudyLanguagePreferences(), "Fixture lesson") }
        val passage = "First fact. Second fact. Third fact."
        val requests = ConcurrentLinkedQueue<TutorTurnRequest>()
        val resolver = StudySourceResolver { id, _ -> ResolvedStudySource.Text(id, "7", SOURCE_TITLE,
            listOf(StudyPassage(passage, StudySourceLocator.TextRange(0, passage.length))), passage.length, SourceCoverage.FULL) }
        val gateway = object : StudyTutorGateway {
            override val available = true
            override suspend fun respond(request: TutorTurnRequest): TutorTurnResponse {
                requests += request
                check(request.createCards)
                return TutorTurnResponse.Ready("""{"cards":[{"question":"First word?","answer":"First","source":"S1"},{"question":"Second fact word?","answer":"Second","source":"S1"},{"question":"Third fact word?","answer":"Third","source":"S1"}]}""",
                    request.requestId, emptyList(), StudyVerdict.UNVERIFIED, "", null, "fixture-model")
            }
        }
        lateinit var model: StudyViewModel
        compose.runOnUiThread { model = StudyViewModel(session.id, StudyUseCases(repository, sourceResolver = resolver), gateway = gateway); owners.put("lesson", model) }
        val preferences = AppLanguagePreferences(MemoryStorage(), tag)
        var translated = ""
        compose.setContent {
            AppLanguageProvider(preferences) {
                translated = uiText(DRAFT_COUNT_FORMAT, 3)
                HostlessHaloTheme { StudyScreen(model, onBack = {}) }
            }
        }
        compose.waitUntil(5_000) { vmScheduler.runCurrent(); !model.uiState.value.loading && model.uiState.value.sources.size == 1 }
        compose.waitForIdle()
        val selected = model.uiState.value.sources.single()
        assertEquals("literal-source", selected.objectId)
        assertEquals("7", selected.version)
        assertEquals(SourceCoverage.FULL, selected.coverage)
        assertFalse(selected.stale)
        fun diagnostic(): String {
            val request = requests.singleOrNull()
            val persisted = runBlocking { withTimeoutOrNull(1_000) { repository.getPracticeItems(session.id).size } }
            return "calls=${requests.size}; cardRequest=${request?.createCards}; " +
                "sources=${request?.sourceExcerpts?.map { it.objectId to it.version }}; " +
                "requestSessionVersion=${request?.session?.updatedAtEpochMs}; " +
                "uiSessionVersion=${model.uiState.value.session?.updatedAtEpochMs}; " +
                "generating=${model.uiState.value.isGenerating}; notice=${model.uiState.value.notice}; " +
                "projected=${model.uiState.value.practiceItems.size}; persisted=${persisted ?: "read timed out"}"
        }
        compose.runOnIdle { model.createPracticeCards() }
        // A terminal rejection is a setup failure, not a reason to time out before the locale
        // assertions. Report bounded synthetic metadata without any response/source body.
        try {
            compose.waitUntil(5_000) { vmScheduler.runCurrent(); !model.uiState.value.isGenerating && model.uiState.value.notice != null }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError("Practice fixture did not finish before locale assertions: ${diagnostic()}", failure)
        }
        val request = requests.singleOrNull()
        val persisted = runBlocking { repository.getPracticeItems(session.id) }
        assertNotNull("Practice gateway did not receive the fixture request: ${diagnostic()}", request)
        assertEquals("Fixture must persist exactly3 cards before testing localization: ${diagnostic()}", 3, persisted.size)
        compose.waitUntil(5_000) { vmScheduler.runCurrent(); model.uiState.value.practiceItems.size == 3 }
        assertTrue(model.uiState.value.practiceItems.none { it.accepted })
        assertTrue(model.uiState.value.dueReviewItems.isEmpty())
        val english = "3 drafts created. Review and Keep each card before scheduling."
        assertEquals(english, model.uiState.value.notice)
        assertTrue("Count notice needs the existing formatted catalogue contract", uiCatalogue.containsKey(DRAFT_COUNT_FORMAT))
        assertTrue(translated.contains("3")); assertNotEquals("English count notice leaked in $tag", english, translated)
        compose.onNodeWithText(translated).assertIsDisplayed()
        compose.onNodeWithText(english).assertDoesNotExist()
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
        private const val SOURCE_TITLE = "Literal source title"
        private const val DRAFT_COUNT_FORMAT = "%1\$d drafts created. Review and Keep each card before scheduling."
    }
}
