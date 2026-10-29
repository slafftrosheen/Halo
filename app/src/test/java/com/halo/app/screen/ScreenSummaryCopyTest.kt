package com.halo.app.screen

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.halo.app.daily.DailyAssistantAction
import com.halo.app.daily.DailyAssistantItem
import com.halo.app.daily.DailyAssistantKind
import com.halo.app.daily.DailyAssistantPriority
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ScreenSummaryCopyTest {
    private fun resources(language: String) = ApplicationProvider.getApplicationContext<Application>().let { context ->
        context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }).resources
    }

    private fun result(summary: String = "1 of 3 actions finished") = ScreenItem(
        "automation:routine", ScreenSourceType.AUTOMATION, "routine", "Study review", summary,
        ScreenContentType.SUMMARY, null, null, null, ScreenItemState.READY, ScreenSensitivity.PRIVATE,
        null, emptyList(), sourceRevision = "exact-revision",
        appSummary = ScreenAppSummary.RoutineActionsFinished(1, 3),
    )

    @Test fun typedCompletionUsesCurrentUiLocaleWithoutChangingSourceText() {
        val item = result()
        val expected = mapOf(
            "en" to "Actions completed: 1 of 3",
            "ru" to "Выполнено действий: 1 из 3",
            "lv" to "Pabeigtās darbības: 1 no 3",
            "pl" to "Wykonane działania: 1 z 3",
        )
        expected.forEach { (language, text) -> assertEquals(language, text, ScreenSummaryCopy.summary(item, resources(language))) }
        assertEquals("Study review", item.title)
        assertEquals("1 of 3 actions finished", item.summary)
        assertEquals("exact-revision", item.sourceRevision)
    }

    @Test fun legacyEnglishCompletionIsNeverParsedOrTranslated() {
        val item = result().copy(appSummary = null)
        assertEquals(item.summary, ScreenSummaryCopy.summary(item, resources("ru")))
    }

    @Test fun sourceProseMatchingACatalogueKeyRemainsVerbatim() {
        val item = result("Study review").copy(appSummary = null)
        assertEquals("Study review", ScreenSummaryCopy.summary(item, resources("ru")))
    }

    @Test fun researchCannotAcquireAnAppCompletionFromOptionalMetadata() {
        val item = result("Original cited passage").copy(sourceType = ScreenSourceType.RESEARCH)
        assertEquals("Original cited passage", ScreenSummaryCopy.summary(item, resources("ru")))
    }

    @Test fun invalidInMemoryCountsFallBackToUnchangedSourceSummary() {
        listOf(-1 to 3, 4 to 3, 0 to -1).forEach { (completed, total) ->
            val item = result().copy(appSummary = ScreenAppSummary.RoutineActionsFinished(completed, total))
            assertEquals(item.summary, ScreenSummaryCopy.summary(item, resources("ru")))
        }
    }

    @Test fun staleResultStillDescribesLastGoodCompletion() {
        val item = result().copy(state = ScreenItemState.STALE)
        assertEquals("Выполнено действий: 1 из 3", ScreenSummaryCopy.summary(item, resources("ru")))
    }

    @Test fun onlyTypedStudyReviewKindGetsAppLabelLocalization() {
        val review = DailyAssistantItem("study:due", DailyAssistantKind.STUDY_REVIEW, DailyAssistantPriority.DUE,
            "Study review", "2 cards due", DailyAssistantAction.OpenStudy("session", true), "study:due", null)
        assertEquals("Повторение материала", ScreenSummaryCopy.dailyTitle(review, resources("ru")))
        assertEquals("Study review", ScreenSummaryCopy.dailyTitle(review.copy(kind = DailyAssistantKind.ATTENTION), resources("ru")))
        assertEquals("2 cards due", review.summary)
    }
}
