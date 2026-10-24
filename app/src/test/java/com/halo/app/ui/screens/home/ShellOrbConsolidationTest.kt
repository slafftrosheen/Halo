package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.presence.LocalShellHaloOrb
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ShellOrbConsolidationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shellOwnsLaunchWhileComposerRetainsTypingSendAndStop() {
        val draft = mutableStateOf("")
        val busy = mutableStateOf(false)
        var sends = 0
        var stops = 0
        var talks = 0
        compose.setContent {
            HostlessHaloTheme {
                CompositionLocalProvider(LocalShellHaloOrb provides true) {
                    HaloComposer(draft.value, { draft.value = it }, { sends++ }, { talks++ }, { stops++ },
                        !busy.value, true, false, "Talk", busy.value)
                }
            }
        }
        compose.onNodeWithContentDescription("Talk").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("Keep this exact question")
        compose.onNodeWithContentDescription("Send").assertIsDisplayed().performClick()
        assertEquals("Keep this exact question", draft.value)
        assertEquals(1, sends)
        assertEquals(0, talks)
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithContentDescription("Stop").assertIsDisplayed().performClick()
        assertEquals(1, stops)
        assertEquals("Keep this exact question", draft.value)
    }

    @Test fun standaloneHostKeepsVoiceLauncherAndShellHidesOnlyHeaderOrb() {
        val shellOwns = mutableStateOf(false)
        var talks = 0
        compose.setContent {
            HostlessHaloTheme {
                CompositionLocalProvider(LocalShellHaloOrb provides shellOwns.value) {
                    Column {
                        HomePresenceHeader(HaloSignalPresentation(), "Ready when you are", compact = true,
                            orbControl = { Text("Local presence launcher") })
                        HaloComposer("", {}, {}, { talks++ }, {}, true, true, false, "Talk", false)
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Talk").performClick()
        assertEquals(1, talks)
        compose.onNodeWithText("Local presence launcher").assertIsDisplayed()
        compose.runOnIdle { shellOwns.value = true }
        compose.onNodeWithText("Local presence launcher").assertDoesNotExist()
        compose.onNodeWithContentDescription("Talk").assertDoesNotExist()
        compose.onNodeWithText("Ready when you are").assertIsDisplayed()
    }

    @Test fun shellOwnershipNeverChangesComposerAdmissionForTypedOrStopActions() {
        assertEquals(HomeComposerAction.NONE, homeComposerPrimaryAction("", true, true, false, true, true))
        assertEquals(HomeComposerAction.SEND, homeComposerPrimaryAction("Question", true, true, false, true, true))
        assertEquals(HomeComposerAction.NONE, homeComposerPrimaryAction("Question", false, true, false, true, true))
        assertEquals(HomeComposerAction.NONE, homeComposerPrimaryAction("Question", true, true, false, false, true))
        assertEquals(HomeComposerAction.STOP, homeComposerPrimaryAction("", false, false, true, false, true))
    }
}
