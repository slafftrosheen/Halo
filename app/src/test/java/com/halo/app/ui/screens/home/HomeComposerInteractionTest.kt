package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
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
        val maxHeight = with(compose.density) { 112.dp.toPx() }
        assertTrue("Idle composer should fit its field and compact action row, height=$height", height <= maxHeight)
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
        compose.onNodeWithContentDescription("Send").assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Set up voice").assertDoesNotExist()
        assertEquals(0, setup)
        assertEquals(0, sends)
        compose.onNodeWithText("Draft").assertExists()
    }

    @Test fun attachmentAndToolsPreserveTheDraftWithoutDispatch() {
        val draft = mutableStateOf("Keep this draft")
        var attachments = 0
        var tools = 0
        var dispatches = 0
        compose.setContent {
            HaloTheme {
                Box(Modifier.width(360.dp)) {
                    HaloComposer(draft.value, { draft.value = it }, { dispatches++ }, { dispatches++ }, {},
                        false, false, false, "Talk", false,
                        onAttach = { attachments++ }, onTools = { tools++ })
                }
            }
        }
        compose.onNodeWithContentDescription("Attach").assertIsDisplayed().performClick()
        compose.onNode(hasText("Tools") or hasContentDescription("Tools")).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput(" more")
        assertEquals(1, attachments)
        assertEquals(1, tools)
        assertEquals(0, dispatches)
        assertTrue(draft.value.contains("more"))
    }

    @Test @Config(qualifiers = "w560dp-h320dp")
    fun shortKeyboardViewportKeepsTheFieldAndAllActionsReachable() {
        var attachments = 0
        var tools = 0
        var sends = 0
        compose.setContent {
            HaloTheme {
                Box(Modifier.width(520.dp).height(96.dp)) {
                    HaloComposer("A question", {}, { sends++ }, {}, {}, true, true, false, "Talk", false,
                        onAttach = { attachments++ }, onTools = { tools++ })
                }
            }
        }
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        listOf("Attach", "Tools", "Send").forEach { label ->
            val node = compose.onNodeWithContentDescription(label)
            node.assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
            val bottom = node.fetchSemanticsNode().boundsInRoot.bottom
            assertTrue("$label exceeds the short viewport", bottom <= with(compose.density) { 96.dp.toPx() })
        }
        assertEquals(1, attachments)
        assertEquals(1, tools)
        assertEquals(1, sends)
    }
}
