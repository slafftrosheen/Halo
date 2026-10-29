package com.halo.app.ui.screens.study

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.language.AppLanguage
import com.halo.app.language.AppLanguagePreferences
import com.halo.app.language.AppLanguageProvider
import com.halo.app.language.resolveUiText
import com.halo.sdk.runtime.Storage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StudyCardReviewCopyTest {
    @get:Rule val compose = createComposeRule()
    private val literal = "Correct. Next review in 2 day(s)."
    private val sourceText = "Correct. This card is now marked mastered."

    @Test fun typedIntervalFormatsInFourLocalesWhileCanonicalAndSourceFeedbackStayLiteral() {
        val language = AppLanguagePreferences(MemoryStorage(), "en")
        var result by mutableStateOf(CardReviewResult("card", true, null, literal,
            meaningComparison = sourceText, nextReviewIntervalDays = 2))
        compose.setContent {
            AppLanguageProvider(language) {
                Column {
                    Text(localizedCardReviewMessage(result), Modifier.testTag("review-status"))
                    Text(sourceText, Modifier.testTag("source-question"))
                    Text(sourceText, Modifier.testTag("source-answer"))
                    Text(result.meaningComparison.orEmpty(), Modifier.testTag("ai-feedback"))
                }
            }
        }
        for (locale in listOf(AppLanguage.ENGLISH, AppLanguage.RUSSIAN, AppLanguage.LATVIAN, AppLanguage.POLISH)) {
            for (days in listOf(1, 2, 5, 21)) {
                compose.runOnIdle { language.select(locale); result = result.copy(nextReviewIntervalDays = days) }
                val expected = when (locale) {
                    AppLanguage.RUSSIAN -> "Верно. Следующее повторение через $days дн."
                    AppLanguage.LATVIAN -> "Pareizi. Nākamā atkārtošana pēc $days d."
                    AppLanguage.POLISH -> "Poprawnie. Następna powtórka za $days dn."
                    else -> "Correct. Next review in $days day(s)."
                }
                compose.onNodeWithTag("review-status").assertTextEquals(expected)
                for (tag in listOf("source-question", "source-answer", "ai-feedback"))
                    compose.onNodeWithTag(tag).assertTextEquals(sourceText)
                assertEquals(literal, result.message)
            }
        }
    }

    @Test fun absentInvalidOrNonCorrectTypedMetadataKeepsTheExistingCanonicalFallback() {
        var result by mutableStateOf(CardReviewResult("card", true, null, literal))
        val language = AppLanguagePreferences(MemoryStorage(), "ru")
        val base = ApplicationProvider.getApplicationContext<Context>()
        val resources = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("ru")) }).resources
        compose.setContent { AppLanguageProvider(language) { Text(localizedCardReviewMessage(result), Modifier.testTag("status")) } }
        for (candidate in listOf(
            result,
            result.copy(nextReviewIntervalDays = -1),
            result.copy(verdict = false, nextReviewIntervalDays = 2),
            result.copy(verdict = null, nextReviewIntervalDays = 2),
            result.copy(message = "Correct. This card is now marked mastered."),
            result.copy(message = "Answer checked, but the schedule could not be updated"),
            result.copy(message = "Halo cannot check this answer automatically. Compare both and mark yourself."),
            result.copy(message = "Not quite. This card goes back to the start of the ladder.", verdict = false),
            result.copy(message = "Uncatalogued literal feedback"),
        )) {
            compose.runOnIdle { result = candidate }
            val expected = resolveUiText(resources, candidate.message)
            compose.onNodeWithTag("status").assertTextEquals(expected)
            if (candidate.message != literal && candidate.message != "Uncatalogued literal feedback")
                assertNotEquals(candidate.message, expected)
        }
    }

    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any>()
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
