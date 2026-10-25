package com.halo.app.ui.screens.study

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.halo.app.language.AppLanguagePreferences
import com.halo.app.language.AppLanguageProvider
import com.halo.app.study.StudyMasteryModel
import com.halo.app.study.StudyMasterySnapshot
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.sdk.runtime.Storage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production badge with typed synthetic metadata; no source text, grading or provider calls. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h900dp")
class StudyMasteryLocalizationTest {
    @get:Rule val compose = createComposeRule()

    private data class Copy(
        val unknown: String, val measured: String, val zero: String,
        val fresh: String, val due: String, val later: String, val memory: String,
        val unmeasured: String, val short: String, val day: String, val days: String, val months: String,
    )

    @Test fun englishMasteryAndDurationsRemainReadableAtBothFontScales() = verify("en", Copy(
        "Mastery —", "Mastery 75%", "Mastery 0%", "New card", "Due now", "Due in 2d", "Memory: ",
        "Not measured yet", "less than a day", "about 1 day", "about 3 days", "about 2 months"))

    @Test fun russianMasteryAndDurationsUseRussianHostCopy() = verify("ru", Copy(
        "Освоено: —", "Освоено: 75%", "Освоено: 0%", "Новая карточка", "Пора повторить", "Повторение через 2 дн.", "Память: ",
        "Ещё не измерено", "меньше суток", "около суток", "около 3 дн.", "около 2 мес."))

    @Test fun latvianMasteryAndDurationsUseLatvianHostCopy() = verify("lv", Copy(
        "Apgūts: —", "Apgūts: 75%", "Apgūts: 0%", "Jauna kartīte", "Jāatkārto tagad", "Atkārtojums pēc 2 d.", "Atmiņa: ",
        "Vēl nav izmērīts", "mazāk par dienu", "apmēram 1 diena", "apmēram 3 d.", "apmēram 2 mēn."))

    @Test fun polishMasteryAndDurationsUsePolishHostCopy() = verify("pl", Copy(
        "Opanowanie: —", "Opanowanie: 75%", "Opanowanie: 0%", "Nowa fiszka", "Czas na powtórkę", "Powtórka za 2 d.", "Pamięć: ",
        "Jeszcze nie zmierzono", "mniej niż dzień", "około 1 dnia", "około 3 dni", "około 2 mies."))

    private fun verify(tag: String, text: Copy) {
        val displayedSnapshot = mutableStateOf(snapshot(measured = false))
        val scale = mutableFloatStateOf(1f)
        val preferences = AppLanguagePreferences(MemoryStorage(), tag)
        compose.setContent {
            AppLanguageProvider(preferences) {
                HostlessHaloTheme(fontScale = scale.floatValue) {
                    Box(Modifier.width(360.dp)) {
                        Column {
                            MasteryBadge(displayedSnapshot.value, NOW)
                            Text("Plain label control", Modifier.testTag("layout-control"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        data class Case(val value: StudyMasterySnapshot, val headline: String, val status: String, val memory: String)
        val cases = listOf(
            Case(snapshot(measured = false), text.unknown, text.fresh, text.unmeasured),
            Case(snapshot(stability = .5), text.measured, text.due, text.short),
            Case(snapshot(stability = 1.4), text.measured, text.due, text.day),
            Case(snapshot(stability = 3.2, due = NOW + 2 * StudyMasteryModel.DAY_MS), text.measured, text.later, text.days),
            Case(snapshot(stability = 60.8), text.measured, text.due, text.months),
            Case(snapshot().copy(masteredProbability = 0.0), text.zero, text.due, text.days),
        )
        for (font in listOf(1f, 2f)) for (case in cases) {
            compose.runOnIdle { displayedSnapshot.value = case.value; scale.floatValue = font }
            val control = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("layout-control").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(control) }
            assertReadable(control.single(), "control font=$font")
            compose.onNodeWithText(case.headline, useUnmergedTree = true).assertExists()
            val label = compose.onNodeWithText("${case.status} · ${text.memory}${case.memory}", useUnmergedTree = true)
            label.assertExists()
            val layouts = mutableListOf<TextLayoutResult>()
            label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertReadable(layouts.single(), "$tag font=$font measured=${case.value.isMeasured} stability=${case.value.stabilityDays}")
        }
    }

    private fun assertReadable(layout: TextLayoutResult, context: String) {
        val diagnostic = "$context size=${layout.size} canvasWidth=${layout.multiParagraph.width} " +
            "paragraphHeight=${layout.multiParagraph.height} lines=${layout.lineCount} " +
            "widthOverflow=${layout.didOverflowWidth} heightOverflow=${layout.didOverflowHeight}"
        println(diagnostic)
        assertTrue(diagnostic, layout.lineCount > 0)
        // Check rendered lines and complete text, allowing one physical pixel for rounding.
        // The paragraph-canvas overflow flag also fires for the plain-text control.
        for (line in 0 until layout.lineCount) {
            assertFalse(diagnostic, layout.isLineEllipsized(line))
            assertTrue(diagnostic, layout.getLineLeft(line) >= -1f)
            assertTrue(diagnostic, layout.getLineRight(line) <= layout.size.width + 1f)
            assertTrue(diagnostic, layout.getLineBottom(line) <= layout.size.height + 1f)
        }
        assertEquals(diagnostic, layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
    }

    private fun snapshot(measured: Boolean = true, stability: Double = 3.2, due: Long = NOW) = StudyMasterySnapshot(
        masteredProbability = .75, retrievability = .8, stabilityDays = stability, difficulty = 5.0,
        reviewCount = if (measured) 1 else 0, lastReviewedAtEpochMs = if (measured) NOW - StudyMasteryModel.DAY_MS else null,
        dueAtEpochMs = due, version = 1,
    )

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

    companion object { private const val NOW = 1_800_000_000_000L }
}
