package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    @Test fun progressiveTypingSubmitsTheWholeVisibleDraft() {
        val drafts = mutableStateOf(hashMapOf<String, String>())
        var submitted = ""
        compose.setContent {
            val current = drafts.value["conversation"].orEmpty()
            fun update(value: String) { drafts.value = HashMap(drafts.value).apply { put("conversation", value) } }
            fun submit() { submitted = current }
            HaloTheme {
                HaloComposer(current, { update(it) }, { submit() }, {}, {}, true, true, false, "Talk", false)
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("C")
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("reate a note containing QA55")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Send").performClick()
        assertEquals("Create a note containing QA55", submitted)
    }

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
    @Test @Config(qualifiers = "w360dp-h800dp")
    fun shellLargeTextKeepsFullWidthDraftAboveAllFourControls() {
        val draft = mutableStateOf("Keep this draft")
        var sends = 0
        var attachments = 0
        var tools = 0
        compose.setContent {
            HaloTheme(fontScale = 2f) {
                androidx.compose.runtime.CompositionLocalProvider(com.halo.app.ui.presence.LocalShellHaloOrb provides true) {
                    Box(Modifier.width(320.dp)) {
                        HaloComposer(draft.value, { draft.value = it }, { sends++ }, {}, {}, true, true, false, "Talk", false,
                            onAttach = { attachments++ }, onTools = { tools++ },
                            surfaceSwitcher = { androidx.compose.material3.Text("Chat / Screen") })
                    }
                }
            }
        }
        val field = compose.onNodeWithTag("home_composer_editor", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Enlarged draft needs the full row", field.width >= with(compose.density) { 280.dp.toPx() })
        listOf("Chat or Screen", "Attach", "Tools", "Send").forEach { label ->
            val control = compose.onNodeWithContentDescription(label)
            control.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            assertTrue("$label overlaps the draft", control.fetchSemanticsNode().boundsInRoot.top >= field.bottom)
        }
        compose.onNodeWithContentDescription("Attach").performClick()
        compose.onNodeWithContentDescription("Tools").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(" preserved")
        compose.onNodeWithContentDescription("Send").performClick()
        assertEquals(1, attachments); assertEquals(1, tools); assertEquals(1, sends)
        assertTrue(draft.value.contains("preserved"))
        compose.onNodeWithContentDescription("Talk").assertDoesNotExist()
    }

    @Test fun burstHardwareTypingThroughFlowAndShellProjectionKeepsTheExactDraft() {
        val drafts = kotlinx.coroutines.flow.MutableStateFlow(ComposerDraftState())
        val utility = mutableStateOf<com.halo.app.ui.presence.ShellUtilityProjection?>(null)
        var submitted = ""
        compose.setContent {
            HaloTheme {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.halo.app.ui.presence.LocalShellHaloOrb provides true,
                        com.halo.app.ui.presence.LocalShellUtilityBand provides utility,
                    ) {
                        val rendered by drafts.collectAsStateWithLifecycle()
                        com.halo.app.ui.presence.BindShellUtilityBand(
                            com.halo.app.ui.presence.ShellPageOwner("home", "typing-conversation")) {
                            HaloComposer(rendered.text, { text ->
                                // Same synchronous StateFlow publish/async collection boundary as ComposerDraftStore.save.
                                drafts.value = ComposerDraftState(text, drafts.value.revision + 1)
                            }, { submitted = drafts.value.text }, {}, {}, true, true, false, "Talk", false,
                                draftIdentity = "typing-conversation", draftRevision = rendered.revision,
                                readAcknowledgedDraft = { drafts.value })
                        }
                    }
                    utility.value?.let { com.halo.app.ui.presence.RenderShellUtilityBand(it) }
                }
            }
        }
        val editor = compose.onNode(hasSetTextAction())
        editor.performClick()
        // No wait-for-idle between hardware key events: exercise the ADB-like burst path,
        // unlike the original semantic text-insertion test which waits between chunks.
        editor.performKeyInput {
            keyDown(androidx.compose.ui.input.key.Key.ShiftLeft)
            pressKey(androidx.compose.ui.input.key.Key.Q)
            pressKey(androidx.compose.ui.input.key.Key.A)
            keyUp(androidx.compose.ui.input.key.Key.ShiftLeft)
            listOf(androidx.compose.ui.input.key.Key.Six, androidx.compose.ui.input.key.Key.Two,
                androidx.compose.ui.input.key.Key.Minus, androidx.compose.ui.input.key.Key.D,
                androidx.compose.ui.input.key.Key.I, androidx.compose.ui.input.key.Key.S,
                androidx.compose.ui.input.key.Key.P, androidx.compose.ui.input.key.Key.L,
                androidx.compose.ui.input.key.Key.A, androidx.compose.ui.input.key.Key.Y,
                androidx.compose.ui.input.key.Key.Minus, androidx.compose.ui.input.key.Key.D,
                androidx.compose.ui.input.key.Key.R, androidx.compose.ui.input.key.Key.A,
                androidx.compose.ui.input.key.Key.F, androidx.compose.ui.input.key.Key.T).forEach { pressKey(it) }
        }
        compose.waitForIdle()
        editor.assertTextEquals("QA62-display-draft")
        assertEquals("QA62-display-draft", drafts.value.text)
        compose.onNodeWithContentDescription("Send").performClick()
        assertEquals("QA62-display-draft", submitted)
        // A canonical submission receipt clears the owner asynchronously after Send.
        // The relocated editor must observe that update without a navigation/restart.
        compose.runOnIdle { drafts.value = ComposerDraftState("", drafts.value.revision + 1) }
        editor.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,
            androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithContentDescription("Send").assertDoesNotExist()
        compose.runOnIdle { drafts.value = ComposerDraftState("Recovered failed draft", drafts.value.revision + 1) }
        editor.assertTextEquals("Recovered failed draft")
    }

    @Test fun canonicalClearCannotBePoisonedByALaggingPageTextSnapshot() {
        val canonical = mutableStateOf(ComposerDraftState("Already sent literal", 1))
        val pageText = mutableStateOf("Already sent literal")
        compose.setContent {
            HaloTheme {
                HaloComposer(pageText.value, {}, {}, {}, {}, true, true, false, "Talk", false,
                    draftIdentity = "lagging-page", draftRevision = canonical.value.revision,
                    readAcknowledgedDraft = { canonical.value })
            }
        }
        val editor = compose.onNode(hasSetTextAction())
        // The projected child can see a newer revision before its parent's captured text.
        compose.runOnIdle { canonical.value = ComposerDraftState("", 2) }
        compose.waitForIdle()
        compose.runOnIdle { pageText.value = "" }
        editor.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,
            androidx.compose.ui.text.AnnotatedString("")))
    }

}
