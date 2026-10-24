package com.halo.app.ui.shell

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
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

    private fun start() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = "home") {
                composable("home") { Box {} }
                composable("notebook") { Box {} }
                composable("voice_settings") { Box {} }
                composable("devices") { Box {} }
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
}
