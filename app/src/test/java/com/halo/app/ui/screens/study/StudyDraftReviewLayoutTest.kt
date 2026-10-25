package com.halo.app.ui.screens.study

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.study.StudyPracticeItem
import com.halo.app.data.study.StudyRepository
import com.halo.app.language.AppLanguagePreferences
import com.halo.app.language.AppLanguageProvider
import com.halo.app.language.uiText
import com.halo.app.study.*
import com.halo.app.ui.presence.LocalShellHaloOrb
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import java.util.concurrent.ConcurrentHashMap
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class StudyDraftReviewLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val owners = ViewModelStore()
    private val vmScheduler = TestCoroutineScheduler()
    private var db: AppDatabase? = null
    private lateinit var repository: StudyRepository
    private lateinit var model: StudyViewModel
    private lateinit var card: StudyPracticeItem
    private var pixelsPerDp = 1f
    private var savedCardsLabel = ""
    private var keepCardLabel = ""
    @Before fun prepareCoroutineMain() {
        // Match the count-notice fixture: VM Room/Flow continuations have an explicit
        // scheduler, independent of Compose's paused frame dispatcher.
        Dispatchers.setMain(UnconfinedTestDispatcher(vmScheduler))
    }
    @After fun close() {
        try {
            compose.runOnIdle { owners.clear() }
            vmScheduler.runCurrent()
        } finally {
            try { db?.close() } finally { Dispatchers.resetMain() }
        }
    }

    @Test @Config(qualifiers = "w360dp-h900dp") fun question360At100PercentKeepsWidthAndMastery() = questionWidth(360, 1f)
    @Test @Config(qualifiers = "w360dp-h900dp") fun question360At200PercentKeepsWidthAndMastery() = questionWidth(360, 2f)
    @Test @Config(qualifiers = "w412dp-h900dp") fun question412At100PercentKeepsWidthAndMastery() = questionWidth(412, 1f)
    @Test @Config(qualifiers = "w412dp-h900dp") fun question412At200PercentKeepsWidthAndMastery() = questionWidth(412, 2f)
    @Test @Config(qualifiers = "w360dp-h900dp") fun longGoal360At100PercentIsBoundedAndAccessible() = header(360, 1f)
    @Test @Config(qualifiers = "w360dp-h900dp") fun longGoal360At200PercentIsBoundedAndAccessible() = header(360, 2f)
    @Test @Config(qualifiers = "w412dp-h900dp") fun longGoal412At100PercentIsBoundedAndAccessible() = header(412, 1f)
    @Test @Config(qualifiers = "w412dp-h900dp") fun longGoal412At200PercentIsBoundedAndAccessible() = header(412, 2f)
    @Test @Config(qualifiers = "ru-rRU-w393dp-h900dp") fun russianPhone393QuestionAndMasteryHaveSeparateReadableBands() = questionWidth(393, 1f, "ru", separateBands = true)
    @Test @Config(qualifiers = "ru-rRU-w393dp-h900dp") fun russianPhone393At200PercentQuestionAndMasteryHaveSeparateReadableBands() = questionWidth(393, 2f, "ru", separateBands = true)
    @Test @Config(qualifiers = "ru-rRU-w393dp-h900dp") fun russianPhone393ExactGoalIsBoundedAndAccessible() = header(393, 1f, "ru", PHONE_GOAL)
    @Test @Config(qualifiers = "ru-rRU-w393dp-h900dp") fun russianPhone393ExactGoalAt200PercentIsBoundedAndAccessible() = header(393, 2f, "ru", PHONE_GOAL)
    @Test @Config(qualifiers = "w412dp-h900dp") fun draftAnswerAndSourceAreReviewableBeforeKeepWithoutRecallSideEffects() {
        render(412, 1f)
        openDrafts()
        assertFalse(model.uiState.value.practiceItems.single().accepted)
        assertFalse(model.uiState.value.revealedAnswers.contains(card.id))
        compose.onNodeWithText(LOCATOR).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(ANSWER, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Keep card").assertExists()
        compose.onNodeWithText("Again").assertDoesNotExist()
        compose.onNodeWithText("Almost").assertDoesNotExist()
        compose.onNodeWithText("I knew it").assertDoesNotExist()
        assertTrue(model.uiState.value.dueReviewItems.none { it.id == card.id })
        assertFalse(model.uiState.value.session!!.solutionRevealed)
        assertTrue(runBlocking { repository.getReviewEvents(card.id) }.isEmpty())
        compose.onNodeWithText("Keep card").performScrollTo().assertIsEnabled().performClick()
        try {
            compose.waitUntil(5_000) { vmScheduler.runCurrent(); model.uiState.value.practiceItems.single().accepted }
        } catch (failure: ComposeTimeoutException) {
            val canonical = runBlocking { withTimeoutOrNull(1_000) {
                repository.getPracticeItem(card.id) to repository.getReviewEvents(card.id).size
            } }
            val persisted = canonical?.first
            val projected = model.uiState.value.practiceItems.singleOrNull()
            throw AssertionError("Keep fixture did not project acceptance: " +
                "canonicalRead=${if (canonical == null) "timed out" else "completed"}; " +
                "storedId=${persisted?.id}; storedAccepted=${persisted?.accepted}; storedReviewCount=${persisted?.reviewCount}; " +
                "storedSource=${persisted?.sourceObjectId}; storedSourceVersion=${persisted?.sourceVersion}; " +
                "reviewEvents=${canonical?.second}; projectedId=${projected?.id}; projectedAccepted=${projected?.accepted}; " +
                "generating=${model.uiState.value.isGenerating}; submitting=${model.uiState.value.isSubmitting}; " +
                "notice=${model.uiState.value.notice}", failure)
        }
        val kept = runBlocking { repository.getPracticeItem(card.id)!! }
        assertEquals(card.id, kept.id); assertEquals(card.question, kept.question); assertEquals(card.answer, kept.answer)
        assertEquals(card.sourceObjectId, kept.sourceObjectId); assertEquals(card.sourceVersion, kept.sourceVersion)
        assertEquals(0, kept.reviewCount)
        // Reviewing a draft must not implicitly reveal an accepted recall card.
        compose.onNodeWithText(ANSWER, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Reveal").performScrollTo().performClick()
        compose.onNodeWithText(ANSWER, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertFalse(model.uiState.value.session!!.solutionRevealed)
        assertTrue(runBlocking { repository.getReviewEvents(card.id) }.isEmpty())
    }

    private fun questionWidth(width: Int, scale: Float, language: String = "en", separateBands: Boolean = false) {
        render(width, scale, languageTag = language)
        openDrafts()
        val question = compose.onNodeWithText(QUESTION, useUnmergedTree = true).performScrollTo()
        val layout = layout(question)
        val mastery = compose.onNodeWithText("Mastery", substring = true, useUnmergedTree = true)
        if (separateBands) {
            val questionBand = question.getUnclippedBoundsInRoot()
            val masteryBand = mastery.getUnclippedBoundsInRoot()
            println("Study fixture lang=$language width=$width font=$scale questionWidthDp=${layout.size.width / pixelsPerDp} questionBand=$questionBand masteryBand=$masteryBand")
            assertTrue("At the observed RU phone width, mastery must not consume the same question text band",
                questionBand.bottom <= masteryBand.top + 1.dp || masteryBand.bottom <= questionBand.top + 1.dp)
        }
        val minimumDp = minOf(220f, (width - 72f) * .75f)
        assertTrue("Question has only ${layout.size.width / pixelsPerDp}dp; mastery must not squeeze it into a word column",
            layout.size.width / pixelsPerDp >= minimumDp)
        mastery.performScrollTo().assertIsDisplayed()
        assertNotNull(model.uiState.value.mastery[card.id])
        compose.onNodeWithText(keepCardLabel).assertExists()
        assertEquals(LOCATOR, model.uiState.value.practiceItems.single().sourceLocator)
    }
    private fun header(width: Int, scale: Float, language: String = "en", goal: String = GOAL) {
        render(width, scale, goal, language)
        val title = compose.onNodeWithText(goal, useUnmergedTree = true)
        val layout = layout(title)
        println("Study header fixture lang=$language width=$width font=$scale titleLines=${layout.lineCount} titleWidthDp=${layout.size.width / pixelsPerDp} titleHeightDp=${layout.size.height / pixelsPerDp}")
        assertTrue("A long Study goal must not become a five-line app bar", layout.lineCount <= 2)
        assertTrue("Keep the complete title for accessibility", title.fetchSemanticsNode().config[SemanticsProperties.Text].any { it.text == goal })
        assertTrue("Title must stay within its two-line typography budget", layout.size.height / pixelsPerDp <= 2 * 25 * scale + 2)
    }
    private fun layout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
    private fun openDrafts() {
        compose.onNodeWithText(savedCardsLabel).performScrollTo().performClick()
        compose.onNodeWithText(QUESTION, useUnmergedTree = true).performScrollTo().assertExists()
    }
    private fun render(width: Int, scale: Float, goal: String = "Fixture lesson", languageTag: String = "en") {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build().also { db = it }
        repository = StudyRepository(database, database.studyDao())
        val session = runBlocking { repository.createSession(listOf("source"), 7, StudyLanguagePreferences(), goal) }
        card = runBlocking { repository.addPracticeDraft(session.id, QUESTION, ANSWER, "source", 7, sourceLocator = LOCATOR) }
        val passage = "The greenhouse starts with forty-eight seedlings."
        val resolver = StudySourceResolver { id, _ -> ResolvedStudySource.Text(id, "7", "Literal source",
            listOf(StudyPassage(passage, StudySourceLocator.TextRange(0, passage.length))), passage.length, SourceCoverage.FULL) }
        compose.runOnUiThread { model = StudyViewModel(session.id, StudyUseCases(repository, sourceResolver = resolver)); owners.put("lesson", model) }
        val language = AppLanguagePreferences(MemoryStorage(), languageTag)
        compose.setContent {
            AppLanguageProvider(language) {
                savedCardsLabel = uiText("Saved cards (%1\$d)", 1)
                keepCardLabel = uiText("Keep card")
                CompositionLocalProvider(LocalShellHaloOrb provides true) {
                    HostlessHaloTheme(fontScale = scale) {
                        pixelsPerDp = LocalDensity.current.density
                        Box(Modifier.width(width.dp).height(900.dp)) { StudyScreen(model, onBack = {}) }
                    }
                }
            }
        }
        compose.waitUntil(5_000) { vmScheduler.runCurrent(); !model.uiState.value.loading && model.uiState.value.practiceItems.size == 1 && model.uiState.value.mastery.containsKey(card.id) }
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
        private const val QUESTION = "How many seedlings are in the test greenhouse before any are moved outside?"
        private const val ANSWER = "48 seedlings"
        private const val LOCATOR = "Fixture source paragraph"
        private const val GOAL = "Use the attached greenhouse file to calculate the seedlings left inside, then explain why."
        private const val PHONE_GOAL = "Use the attached greenhouse file to calculate the seedlings left inside."
    }
}
