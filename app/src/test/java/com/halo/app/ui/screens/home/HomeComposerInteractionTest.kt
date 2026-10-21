package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.HostlessHaloTheme as HaloTheme
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

    @Test fun idleComposerLeavesRoomForTheConversation() {
        compose.setContent {
            HaloTheme {
                Box(Modifier.width(360.dp).testTag("composer")) {
                    HaloComposer("", {}, {}, {}, {}, true, true, false, "Talk", false)
                }
            }
        }
        val height = compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot.height
        val maxHeight = with(compose.density) { 88.dp.toPx() }
        assertTrue("Idle composer should fit one field/action row, height=$height", height <= maxHeight)
    }

    @Test fun typedDraftShowsSendAndNeverStartsVoice() {
        var talks = 0
        var sends = 0
        val draft = mutableStateOf("Keep this typed draft")
        compose.setContent {
            HaloTheme {
                HaloComposer(draft.value, { draft.value = it }, { sends++ }, { talks++ }, {},
                    true, true, false, "Talk", false)
            }
        }
        compose.onNodeWithContentDescription("Talk").assertDoesNotExist()
        compose.onNodeWithContentDescription("Send").assertIsDisplayed().performClick()
        assertEquals(0, talks)
        assertEquals(1, sends)
        assertEquals("Keep this typed draft", draft.value)
        compose.onNodeWithContentDescription("Dictate instead").assertDoesNotExist()
        compose.onNodeWithText("Use text").assertDoesNotExist()
    }

    @Test fun busyActionBecomesStopWhileKeepingTheEditableDraftAndTargetPosition() {
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
        val sendPosition = compose.onNodeWithContentDescription("Send").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Stop").assertDoesNotExist()
        compose.onNodeWithContentDescription("Send").performClick()
        assertEquals(1, sends)
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithContentDescription("Send").assertDoesNotExist()
        compose.onNodeWithContentDescription("Talk").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput(" edited")
        assertTrue(draft.value.contains("edited"))
        assertEquals(sendPosition, compose.onNodeWithContentDescription("Stop").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("Stop").assertIsEnabled().performClick()
        assertEquals(1, sends)
        assertEquals(1, stops)
    }

    @Test fun stopDoesNotSubmitTheDraftOrStartVoice() {
        var talks = 0
        var sends = 0
        var stops = 0
        compose.setContent {
            HaloTheme(darkTheme = false) {
                HaloComposer("Next question", {}, { sends++ }, { talks++ }, { stops++ },
                    enabled = false, voiceEnabled = true, voiceActive = false,
                    voiceContentDescription = "Interrupt reply and talk", showStop = true)
            }
        }
        compose.onNodeWithContentDescription("Send").assertDoesNotExist()
        compose.onNodeWithContentDescription("Interrupt reply and talk").assertDoesNotExist()
        compose.onNodeWithContentDescription("Stop").performClick()
        assertEquals(1, stops)
        assertEquals(0, talks)
        assertEquals(0, sends)
    }

    @Test fun everydaySuggestionPreparesEditableTextWithoutSending() {
        val draft = mutableStateOf("")
        var sends = 0
        compose.setContent {
            HaloTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        HomeWelcome(compact = true) { draft.value = it }
                    }
                    HaloComposer(draft.value, { draft.value = it }, { sends++ }, {}, {},
                        true, true, false, "Talk", false)
                }
            }
        }
        compose.onNodeWithText("Make a plan").performClick()
        compose.runOnIdle {
            assertEquals("Help me plan my day. Ask me what I need to get done.", draft.value)
            assertEquals(0, sends)
        }
        compose.onNode(hasSetTextAction()).performTextInput(" Add a lunch break.")
        compose.onNodeWithContentDescription("Send").assertIsDisplayed().performClick()
        assertEquals(1, sends)
    }

    @Test @Config(qualifiers = "ru-rRU")
    fun enlargedTranslatedActionsKeepAccessibleTouchTargets() {
        val draft = mutableStateOf("")
        compose.setContent {
            HaloTheme(darkTheme = false, fontScale = 2f) {
                Box(Modifier.width(320.dp)) {
                    HaloComposer(draft.value, { draft.value = it }, {}, {}, {}, true, true, false, "Говорить", false)
                }
            }
        }
        compose.onNodeWithContentDescription("Говорить").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.runOnIdle { draft.value = "Вопрос" }
        compose.onNodeWithContentDescription("Отправить").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test fun focusGateKeepsTheDraftVisibleAndPreventsVoiceOrSubmission() {
        var setup = 0
        var sends = 0
        compose.setContent {
            HaloTheme(darkTheme = true, fontScale = 2f) {
                Box(Modifier.width(320.dp)) {
                    HaloComposer("Draft", {}, { sends++ }, { setup++ }, {}, false, true, false,
                        "Set up voice", false, focusAllowed = false)
                }
            }
        }
        compose.onNodeWithContentDescription("Set up voice").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Send").assertDoesNotExist()
        assertEquals(0, setup)
        assertEquals(0, sends)
        compose.onNodeWithText("Draft").assertExists()
    }
}
