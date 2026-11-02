package com.halo.app.ui.shell

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.halo.app.ui.everyday.EverydayRoutes
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class EverydayEntryNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController
    private fun start(): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = "home") {
                composable("home") { Box {} }
                composable("screen/brief?briefId={briefId}", arguments = listOf(navArgument("briefId") { type = NavType.StringType; nullable = true })) { Box {} }
                composable("screen/organizer?recordId={recordId}", arguments = listOf(navArgument("recordId") { type = NavType.StringType; nullable = true })) { Box {} }
            }
        }
        compose.waitForIdle()
        return restoration
    }

    @Test fun warmNoticesOpenExactEntriesEvenAfterThePreviousPageSelectedDifferentHistory() {
        start()
        val morning = "a".repeat(64); val evening = "b".repeat(64)
        var first = ""
        compose.runOnIdle { nav.navigateToInstrument(EverydayRoutes.brief(morning)) }
        compose.runOnIdle { first = nav.currentBackStackEntry!!.id; nav.currentBackStackEntry!!.savedStateHandle["brief.requestedId"] = "history-selected-inside-page" }
        compose.runOnIdle { nav.navigateToInstrument(EverydayRoutes.brief(evening)) }
        compose.runOnIdle {
            assertNotEquals(first, nav.currentBackStackEntry!!.id)
            assertEquals(evening, nav.currentBackStackEntry!!.arguments!!.getString("briefId"))
            assertNull(nav.currentBackStackEntry!!.savedStateHandle.get<String>("brief.requestedId"))
        }
        compose.runOnIdle { nav.popBackStack() }
        compose.runOnIdle { assertEquals("history-selected-inside-page", nav.currentBackStackEntry!!.savedStateHandle.get<String>("brief.requestedId")) }
        // The same morning notice is an explicit new request, not the old internally-selected history.
        compose.runOnIdle { nav.navigateToInstrument(EverydayRoutes.brief(morning)) }
        compose.runOnIdle {
            assertNotEquals(first, nav.currentBackStackEntry!!.id)
            assertEquals(morning, nav.currentBackStackEntry!!.arguments!!.getString("briefId"))
            assertNull(nav.currentBackStackEntry!!.savedStateHandle.get<String>("brief.requestedId"))
        }
    }

    @Test fun newOrganizerTargetPreservesPreviousDraftThroughNavigationAndRestoration() {
        val restoration = start()
        compose.runOnIdle { nav.navigateToInstrument(EverydayRoutes.organizer("record-one")) }
        compose.runOnIdle { nav.currentBackStackEntry!!.savedStateHandle["organizer.draft"] = "unsaved first title" }
        compose.runOnIdle { nav.navigateToInstrument(EverydayRoutes.organizer("record-two")) }
        compose.runOnIdle {
            assertEquals("record-two", nav.currentBackStackEntry!!.arguments!!.getString("recordId"))
            assertNull(nav.currentBackStackEntry!!.savedStateHandle.get<String>("organizer.draft"))
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals("record-two", nav.currentBackStackEntry!!.arguments!!.getString("recordId")); nav.popBackStack() }
        compose.runOnIdle {
            assertEquals("record-one", nav.currentBackStackEntry!!.arguments!!.getString("recordId"))
            assertEquals("unsaved first title", nav.currentBackStackEntry!!.savedStateHandle.get<String>("organizer.draft"))
        }
    }
}
