package com.halo.app.ui.shell

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Navigation Compose saved stacks: explicit roots must not reopen saved details. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ExactPrimaryNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController

    @Test fun coldNavigationWaitsForTheRealNavHostToAttachItsGraph() {
        val attached = androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            nav = rememberNavController()
            androidx.compose.runtime.LaunchedEffect(Unit) {
                nav.awaitNavigationReady()
                nav.navigateToPrimary("devices", restoreSavedState = false)
            }
            if (attached.value) NavHost(nav, startDestination = "home") {
                composable("home") { Box {} }
                composable("devices") { Box {} }
            }
        }
        compose.runOnIdle { assertEquals(null, nav.currentDestination); attached.value = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("devices", nav.currentDestination?.route) }
    }

    private fun start(startDestination: String = "home") {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = startDestination) {
                composable("home") { Box {} }
                composable("notebook") { Box {} }
                composable("voice_settings") { Box {} }
                composable("devices") { Box {} }
                composable("guided_setup") { Box {} }
            }
        }
        compose.waitForIdle()
    }

    @Test fun repeatedExactWorkspaceRequestsDoNotRestoreVoiceAboveWorkspace() {
        start()
        repeat(2) {
            compose.runOnIdle { nav.navigateToPrimary("notebook", restoreSavedState = false) }
            compose.runOnIdle { nav.navigate("voice_settings") }
            compose.runOnIdle { nav.navigateToPrimary("notebook", restoreSavedState = false) }
            compose.runOnIdle { assertEquals("notebook", nav.currentDestination?.route) }
        }
    }

    @Test fun exactRootDoesNotRestoreAnotherRootsSavedChildStack() {
        start()
        compose.runOnIdle { nav.navigateToPrimary("notebook") }
        compose.runOnIdle { nav.navigate("voice_settings") }
        compose.runOnIdle { nav.navigateToPrimary("devices") }
        compose.runOnIdle { nav.navigateToPrimary("notebook", restoreSavedState = false) }
        compose.runOnIdle { assertEquals("notebook", nav.currentDestination?.route) }
    }

    @Test fun existingRestorationCallersStillRestoreTheirChildStack() {
        start()
        compose.runOnIdle { nav.navigateToPrimary("notebook") }
        compose.runOnIdle { nav.navigate("voice_settings") }
        compose.runOnIdle { nav.navigateToPrimary("devices") }
        compose.runOnIdle { nav.navigateToPrimary("notebook") }
        compose.runOnIdle { assertEquals("voice_settings", nav.currentDestination?.route) }
    }

    @Test fun deviceSetupReturnsToTheSameResumedGuideAndChecks() {
        start()
        compose.runOnIdle { nav.navigateToGuidedSetup() }
        val guide = nav.currentBackStackEntry!!
        compose.runOnIdle { guide.savedStateHandle["listeningVerified"] = true }
        compose.runOnIdle { nav.navigateToPrimary("devices", restoreSavedState = false) }
        compose.runOnIdle { nav.navigate("voice_settings") }
        compose.runOnIdle { nav.navigateToGuidedSetup() }
        compose.runOnIdle {
            assertSame(guide, nav.currentBackStackEntry)
            assertEquals(true, nav.currentBackStackEntry!!.savedStateHandle.get<Boolean>("listeningVerified"))
            assertTrue(nav.popBackStack())
            assertEquals("home", nav.currentDestination?.route)
        }
    }

    @Test fun guideConfigurationDoesNotRestoreAnUnrelatedSavedTab() {
        start()
        compose.runOnIdle { nav.navigateToPrimary("notebook") }
        compose.runOnIdle { nav.navigate("voice_settings") }
        compose.runOnIdle { nav.navigateToPrimary("devices") }
        compose.runOnIdle { nav.navigateToGuidedSetup() }
        val guide = nav.currentBackStackEntry!!
        compose.runOnIdle { nav.navigateToPrimary("notebook") }
        compose.runOnIdle { assertEquals("notebook", nav.currentDestination?.route) }
        compose.runOnIdle { nav.navigateToGuidedSetup() }
        compose.runOnIdle { assertSame(guide, nav.currentBackStackEntry) }
    }

    @Test fun freshSetupCanReturnFromDevicesWithBack() {
        start("guided_setup")
        val guide = nav.currentBackStackEntry!!
        compose.runOnIdle { nav.navigateToPrimary("devices", restoreSavedState = false) }
        compose.runOnIdle {
            assertEquals("devices", nav.currentDestination?.route)
            assertTrue(nav.popBackStack())
            assertSame(guide, nav.currentBackStackEntry)
        }
    }
}
