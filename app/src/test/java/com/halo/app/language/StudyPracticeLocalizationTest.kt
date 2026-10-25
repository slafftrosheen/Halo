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
import com.halo.app.study.*
import com.halo.app.ui.screens.study.StudyScreen
import com.halo.app.ui.screens.study.StudyViewModel
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Host labels use the catalogue; actual source-card status must do so too. Source text stays literal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StudyPracticeLocalizationTest {
    @get:Rule val compose = createComposeRule()
    private val owners = ViewModelStore()
    private var database: AppDatabase? = null
    @After fun close() { owners.clear(); database?.close() }

    @Test fun russianPracticeHostLabelsHaveCatalogueEntriesAndRenderTranslated() = checkLabels("ru")
    @Test fun latvianPracticeHostLabelsHaveCatalogueEntriesAndRenderTranslated() = checkLabels("lv")
    @Test fun polishPracticeHostLabelsHaveCatalogueEntriesAndRenderTranslated() = checkLabels("pl")
    @Test fun russianActualStudySourceCardLocalizesReadyAndPreservesSourceTitle() = checkSourceCard("ru")
    @Test fun latvianActualStudySourceCardLocalizesReadyAndPreservesSourceTitle() = checkSourceCard("lv")
    @Test fun polishActualStudySourceCardLocalizesReadyAndPreservesSourceTitle() = checkSourceCard("pl")

    private fun checkLabels(tag: String) {
        val labels = listOf("Languages", "Ready", INVALID_DRAFT)
        val preferences = AppLanguagePreferences(MemoryStorage(), tag)
        compose.setContent {
            AppLanguageProvider(preferences) {
                HostlessHaloTheme {
                    Column { labels.forEachIndexed { index, key -> Text(uiText(key), Modifier.testTag("study-label-$index")) } }
                }
            }
        }
        labels.forEachIndexed { index, english ->
            assertTrue("Missing Study host catalogue key: $english", uiCatalogue.containsKey(english))
            val rendered = compose.onNodeWithTag("study-label-$index").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
            assertTrue("Translated Study host label must not be blank", rendered.isNotBlank())
            assertNotEquals("English fallback in $tag for $english", english, rendered)
        }
    }
    private fun checkSourceCard(tag: String) {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build().also { database = it }
        val repository = StudyRepository(db, db.studyDao())
        val session = runBlocking { repository.createSession(listOf("literal-source"), 7, StudyLanguagePreferences(), "Fixture lesson") }
        val sourceText = "Literal source passage."
        val resolver = StudySourceResolver { id, _ -> ResolvedStudySource.Text(id, "7", SOURCE_TITLE,
            listOf(StudyPassage(sourceText, StudySourceLocator.TextRange(0, sourceText.length))), sourceText.length, SourceCoverage.FULL) }
        val study = StudyUseCases(repository, sourceResolver = resolver)
        lateinit var model: StudyViewModel
        compose.runOnUiThread { model = StudyViewModel(session.id, study); owners.put("lesson", model) }
        val preferences = AppLanguagePreferences(MemoryStorage(), tag)
        var materialLabel = ""
        var readyLabel = ""
        compose.setContent {
            AppLanguageProvider(preferences) {
                materialLabel = uiText("Material") + " · 1"
                readyLabel = uiText("Ready")
                HostlessHaloTheme { StudyScreen(model, onBack = {}) }
            }
        }
        compose.waitUntil(5_000) { !model.uiState.value.loading && model.uiState.value.sources.size == 1 }
        assertEquals("Ready", model.uiState.value.sources.single().status)
        compose.onNodeWithText(materialLabel).performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(SOURCE_TITLE, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(SOURCE_TITLE).performScrollTo().assertIsDisplayed()
        assertNotEquals("Fixture locale must translate the known status", "Ready", readyLabel)
        compose.onAllNodesWithText(readyLabel, useUnmergedTree = true).onFirst().performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ready", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(SOURCE_TITLE, model.uiState.value.sources.single().title)
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
        private const val INVALID_DRAFT = "Invalid practice draft; no cards were saved"
    }
}
