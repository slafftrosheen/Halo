package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HomeComposerInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sendDoesNotBecomeStopAndTargetsStayInPlace() {
        val busy = mutableStateOf(false)
        val draft = mutableStateOf("A question")
        var sends = 0
        var stops = 0
        compose.setContent {
            HaloTheme(darkTheme = false) {
                Box(Modifier.width(360.dp)) {
                    HaloComposer(draft.value, { draft.value = it }, { sends++ }, {}, { stops++ },
                        enabled = !busy.value, voiceEnabled = !busy.value, voiceActive = false,
                        voiceContentDescription = "Talk", showStop = busy.value)
                }
            }
        }
        val sendPosition = compose.onNodeWithText("Send").fetchSemanticsNode().boundsInRoot
        val stopPosition = compose.onNodeWithText("Stop").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Stop").assertIsNotEnabled()
        compose.onNodeWithText("Send").performClick()
        assertEquals(1, sends)
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithText("Send").assertIsNotEnabled()
        compose.onNodeWithText("Talk").assertIsNotEnabled()
        compose.onNodeWithText("Message").performTextInput(" edited")
        assertTrue(draft.value.contains("edited"))
        assertEquals(sendPosition, compose.onNodeWithText("Send").fetchSemanticsNode().boundsInRoot)
        assertEquals(stopPosition, compose.onNodeWithText("Stop").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Stop").performClick()
        assertEquals(1, sends)
        assertEquals(1, stops)
    }

    @Test fun talkCanInterruptSpeechWithoutSubmittingADraft() {
        var talks = 0
        var sends = 0
        compose.setContent {
            HaloTheme(darkTheme = false) {
                HaloComposer("Next question", {}, { sends++ }, { talks++ }, {},
                    enabled = false, voiceEnabled = true, voiceActive = false,
                    voiceContentDescription = "Interrupt reply and talk", showStop = true)
            }
        }
        compose.onNodeWithText("Send").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Interrupt reply and talk").performClick()
        assertEquals(1, talks)
        assertEquals(0, sends)
    }

    @Test @Config(qualifiers = "ru-rRU")
    fun enlargedTranslatedActionNamesStayWhole() {
        compose.setContent {
            HaloTheme(darkTheme = false, fontScale = 1.5f) {
                Box(Modifier.width(320.dp)) {
                    HaloComposer("", {}, {}, {}, {}, true, true, false, "Говорить", false)
                }
            }
        }
        listOf("Говорить", "Отправить").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            compose.onNodeWithText(label).assertHeightIsAtLeast(48.dp)
        }
    }
}
