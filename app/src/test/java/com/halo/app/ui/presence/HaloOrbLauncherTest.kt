package com.halo.app.ui.presence

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.halo.app.ui.screens.home.HomeToolGroup
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class HaloOrbLauncherTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openingOrbOnlyDiscoversAndShortcutDelegatesTypedGroup() {
        var primary = 0
        var request: HaloToolCatalogRequest? = null
        compose.setContent { HostlessHaloTheme {
            HaloOrbLauncher(HaloSignalPresentation(reducedMotion = true), HaloOrbInteraction(HaloOrbPrimary.TALK, false),
                HaloOrbShortcut.defaults, {}, { primary++ }, {}, { request = it })
        } }
        compose.onNodeWithText("All tools").assertDoesNotExist()
        compose.onNodeWithContentDescription("Halo actions").performClick()
        assertEquals(0, primary); assertNull(request)
        compose.onNodeWithText("Find").performScrollTo().performClick()
        assertEquals(HaloToolCatalogRequest(HomeToolGroup.FIND), request)
        compose.onNodeWithText("All tools").assertDoesNotExist()
        assertEquals(0, primary)
    }

    @Test fun activeOrbUsesStopThenInterruptionWithoutOpeningAnotherVoiceOwner() {
        val interaction = mutableStateOf(HaloOrbInteraction(HaloOrbPrimary.STOP, true))
        val invoked = mutableListOf<HaloOrbPrimary>()
        compose.setContent { HostlessHaloTheme {
            HaloOrbLauncher(HaloSignalPresentation(reducedMotion = true), interaction.value,
                emptySet(), {}, { invoked += it }, {}, {})
        } }
        compose.onNodeWithContentDescription("Stop").performClick()
        compose.runOnIdle { interaction.value = HaloOrbInteraction(HaloOrbPrimary.INTERRUPT, true) }
        compose.onNodeWithContentDescription("Interrupt reply and talk").performClick()
        assertEquals(listOf(HaloOrbPrimary.STOP, HaloOrbPrimary.INTERRUPT), invoked)
        compose.onNodeWithText("All tools").assertDoesNotExist()
    }

    @Test fun approvalMenuKeepsStopSeparateFromReview() {
        var stopped = 0; var reviewed = 0
        compose.setContent { HostlessHaloTheme {
            HaloOrbLauncher(HaloSignalPresentation(reducedMotion = true), HaloOrbInteraction(HaloOrbPrimary.REVIEW, true),
                emptySet(), {}, { reviewed++ }, { stopped++ }, {})
        } }
        compose.onNodeWithContentDescription("Show Halo shortcuts").performClick()
        compose.onNodeWithText("Review").assertIsDisplayed()
        compose.onNodeWithText("Stop").assertIsDisplayed().performClick()
        assertEquals(1, stopped); assertEquals(0, reviewed)
    }

    @Test fun emptyPreferencesKeepAllToolsAndCustomizationDoesNotExecute() {
        val shortcuts = mutableStateOf(emptySet<HaloOrbShortcut>())
        var invoked = 0; var request: HaloToolCatalogRequest? = null
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            HaloOrbLauncher(HaloSignalPresentation(reducedMotion = true), HaloOrbInteraction(HaloOrbPrimary.TALK, false),
                shortcuts.value, { shortcuts.value = it }, { invoked++ }, {}, { request = it })
        } }
        compose.onNodeWithContentDescription("Halo actions").performClick()
        compose.onNodeWithText("All tools").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Choose shortcuts").performScrollTo().performClick()
        compose.onNode(hasText("Find") and isToggleable()).performClick()
        assertEquals(setOf(HaloOrbShortcut.FIND), shortcuts.value)
        assertEquals(0, invoked); assertNull(request)
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithText("All tools").performScrollTo().performClick()
        assertEquals(HaloToolCatalogRequest(HomeToolGroup.CREATE), request)
    }

    @Test fun stateTransitionCollapsesStaleChooserAndKeepsCriticalStop() {
        val interaction = mutableStateOf(HaloOrbInteraction(HaloOrbPrimary.TALK, false))
        var requests = 0
        compose.setContent { HostlessHaloTheme {
            HaloOrbLauncher(HaloSignalPresentation(reducedMotion = true), interaction.value, HaloOrbShortcut.defaults,
                {}, {}, {}, { requests++ })
        } }
        compose.onNodeWithContentDescription("Halo actions").performClick()
        compose.onNodeWithText("All tools").assertExists()
        compose.runOnIdle { interaction.value = HaloOrbInteraction(HaloOrbPrimary.STOP, true) }
        compose.onNodeWithText("All tools").assertDoesNotExist()
        compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
        assertEquals(0, requests)
    }

    @Test fun backgroundDismissesChooserWithoutPerformingItsAction() {
        val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        var invoked = 0
        compose.setContent { HostlessHaloTheme { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            HaloOrbLauncher(HaloSignalPresentation(reducedMotion = true), HaloOrbInteraction(HaloOrbPrimary.TALK, false),
                HaloOrbShortcut.defaults, {}, { invoked++ }, {}, { invoked++ })
        } } }
        compose.onNodeWithContentDescription("Halo actions").performClick()
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("All tools").assertDoesNotExist()
        assertEquals(0, invoked)
    }
}
