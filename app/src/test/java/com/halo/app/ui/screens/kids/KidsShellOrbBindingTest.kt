package com.halo.app.ui.screens.kids

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.presence.*
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class KidsShellOrbBindingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shellDispatchesLatestScopedKidsTargetAndClearsWithoutReplayOnExit() {
        val holder = mutableStateOf<ShellOrbOverride?>(null)
        val target = mutableStateOf("TOPIC")
        val busy = mutableStateOf(false)
        val visible = mutableStateOf(true)
        val called = mutableListOf<String>()
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellOrbOverride provides holder) {
                if (visible.value) {
                    val currentTarget = target.value
                    KidsShellOrbBinding(HaloSignalPresentation(), busy.value,
                        { called += currentTarget }, { called += "stop" })
                }
            }
        } }
        compose.runOnIdle { holder.value!!.onPrimary(HaloOrbPrimary.TALK) }
        compose.runOnIdle { target.value = "ANSWER" }
        compose.runOnIdle { holder.value!!.onPrimary(HaloOrbPrimary.TALK) }
        compose.runOnIdle { busy.value = true }
        compose.runOnIdle {
            assertEquals(HaloOrbPrimary.STOP, holder.value!!.interaction.primary)
            holder.value!!.onPrimary(HaloOrbPrimary.STOP)
            holder.value!!.onStop()
            visible.value = false
        }
        compose.runOnIdle { assertNull(holder.value) }
        assertEquals(listOf("TOPIC", "ANSWER", "stop", "stop"), called)
    }

    @Test fun shellOwnedDockKeepsTypedAndHelpActionsWithoutAnotherMicrophone() {
        var typed = 0
        var helped = 0
        var voice = 0
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellHaloOrb provides true) {
                KidsVoiceDock("Talk or type", false, false, { voice++ }, { typed++ }, {}, { helped++ })
            }
        } }
        compose.onNodeWithContentDescription("Talk to halo").assertDoesNotExist()
        compose.onNodeWithContentDescription("Type instead").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("What can I say?").assertIsDisplayed().performClick()
        assertEquals(1, typed)
        assertEquals(1, helped)
        assertEquals(0, voice)
    }
}
