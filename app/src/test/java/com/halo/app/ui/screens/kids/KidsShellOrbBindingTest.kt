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

    @Test fun childPageLeaseUsesLatestCallbackAndRejectsOldDestinationOrDisabledTyping() {
        val page = mutableStateOf<ShellPageProjection?>(null)
        val destination = mutableStateOf(KidsDestination.HOME)
        val saving = mutableStateOf(false)
        val callbacks = mutableListOf<KidsDestination>()
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellPageActions provides page) {
                val target = destination.value
                KidsZoneBinding(target, null, "Talk or type", saving.value, saving.value,
                    { callbacks += target }, {}, {}, {})
            }
        } }
        lateinit var old: ShellPageProjection
        compose.runOnIdle { old = page.value!!; assertTrue(old.invoke("page.kids.type")); destination.value = KidsDestination.LEARN }
        compose.runOnIdle {
            assertFalse(old.invoke("page.kids.type"))
            assertTrue(page.value!!.invoke("page.kids.type"))
            saving.value = true
        }
        compose.runOnIdle {
            assertFalse(page.value!!.invoke("page.kids.type"))
            assertNull(page.value!!.owner.nextStep)
            assertEquals(listOf(KidsDestination.HOME, KidsDestination.LEARN), callbacks)
        }
    }

    @Test fun systemBackClosesInputThenReturnsToChoicesThroughStopOwner() {
        val input = mutableStateOf(true)
        val destination = mutableStateOf(KidsDestination.LEARN)
        var stopped = 0
        lateinit var host: androidx.activity.ComponentActivity
        compose.setContent { HostlessHaloTheme {
            host = androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity
            KidsSystemBack(destination.value, input.value, { stopped++ }, { input.value = false }, { destination.value = KidsDestination.HOME })
        } }
        compose.runOnIdle { host.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertFalse(input.value); assertEquals(KidsDestination.LEARN, destination.value) }
        compose.runOnIdle { host.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(KidsDestination.HOME, destination.value); assertEquals(2, stopped) }
    }
    @Test fun returningHomeOrCreatingDoesNotKeepAnOldLessonIdentity() {
        val destination = mutableStateOf(KidsDestination.LEARN)
        compose.setContent { HostlessHaloTheme {
            val band = remember { mutableStateOf<ShellUtilityProjection?>(null) }
            CompositionLocalProvider(LocalShellHaloOrb provides true, LocalShellUtilityBand provides band) {
                KidsZoneBinding(destination.value, "Volcano lesson", "Talk or type", false, false, {}, {}, {}, {})
                band.value?.let { RenderShellUtilityBand(it) }
            }
        } }
        compose.onNodeWithText("Volcano lesson · Talk or type").assertIsDisplayed()
        compose.runOnIdle { destination.value = KidsDestination.HOME }
        compose.onNodeWithText("Kids · Talk or type").assertIsDisplayed()
        compose.onNodeWithText("Volcano lesson", substring = true).assertDoesNotExist()
        compose.runOnIdle { destination.value = KidsDestination.CREATE }
        compose.onNodeWithText("My project · Talk or type").assertIsDisplayed()
        compose.onNodeWithText("Volcano lesson", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Kids · Talk or type").assertDoesNotExist()
    }

}
