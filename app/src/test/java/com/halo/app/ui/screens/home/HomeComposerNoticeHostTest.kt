package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.halo.app.ui.presence.*
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class)
class HomeComposerNoticeHostTest {
    @get:Rule val compose = createComposeRule()
    @Test fun relocatedSetupNoticeWrapsAndDisabledSendPreservesEditableDraftAtLargeText() {
        val holder = mutableStateOf<ShellUtilityProjection?>(null)
        val draft = mutableStateOf("Keep my draft")
        val conversation = mutableStateOf("a")
        val setup = mutableStateOf(true)
        var sends = 0
        var talks = 0
        val receipt = TextSubmissionState(TextSubmissionPhase.TIMED_OUT, conversationId = "a")
        compose.setContent {
            HostlessHaloTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f),
                    LocalShellUtilityBand provides holder, LocalShellHaloOrb provides true) {
                    BindShellUtilityBand(ShellPageOwner("home", objectId = conversation.value)) {
                        Column(Modifier.width(320.dp)) {
                            HomeComposerNotice(homeComposerNotice(setup.value, receipt, conversation.value))
                            HaloComposer(draft.value, { draft.value = it }, { sends++ }, { talks++ }, {},
                                false, false, false, "Talk", false)
                        }
                    }
                    holder.value?.let { RenderShellUtilityBand(it) }
                }
            }
        }
        val notice = compose.onNodeWithText("Answers need setup. Choose Set up answers below.")
        notice.assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        notice.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().lineCount > 1)
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled().performTouchInput { click() }
        compose.onNode(hasSetTextAction()).performTextInput(" safely")
        compose.runOnIdle { assertEquals(0, sends); assertEquals(0, talks); assertTrue(draft.value.contains("Keep my draft")) }
        compose.runOnIdle { setup.value = false }
        compose.onNodeWithText("Your message wasn’t accepted. You can try again.").assertIsDisplayed()
        compose.runOnIdle { conversation.value = "b" }
        compose.onNodeWithText("Your message wasn’t accepted. You can try again.").assertDoesNotExist()
    }
}
