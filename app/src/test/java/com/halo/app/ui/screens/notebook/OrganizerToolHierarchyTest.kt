package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.everyday.EverydayRoutes
import com.halo.app.ui.everyday.OrganizerToolLinks
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.app.language.resolveUiText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OrganizerToolHierarchyTest {
    @get:Rule val compose = createComposeRule()
    private val briefTitle get() = resolveUiText(ApplicationProvider.getApplicationContext<Application>().resources, "Your day in Halo")

    @Test fun workspaceListsOnlyTheOrganizerAlongsideTheExistingInstrumentFamilies() {
        var route: String? = null
        compose.setContent { HostlessHaloTheme {
            WorkspaceToolsList({}, {}, {}, {}, {}, { route = it })
        } }
        compose.onNodeWithText("Organizer").assertExists().performClick()
        assertEquals(EverydayRoutes.ORGANIZER, route)
        compose.onNodeWithText(briefTitle).assertDoesNotExist()
        compose.onNodeWithText("World clocks").assertDoesNotExist()
        compose.onNodeWithText("Sources").assertDoesNotExist()
    }

    @Test fun organizerProvidesWorkingToolsAndSourceSettings() {
        var route: String? = null
        compose.setContent { HostlessHaloTheme { OrganizerToolLinks(false) { route = it } } }
        compose.onNodeWithText(briefTitle).performClick()
        assertEquals(EverydayRoutes.BRIEF, route)
        compose.onNodeWithText("World clocks").performClick()
        assertEquals(EverydayRoutes.CLOCKS, route)
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("Sources").performClick()
        assertEquals(EverydayRoutes.SOURCES, route)
    }

    @Test fun childOrganizerDoesNotExposeAdultSourceSettings() {
        compose.setContent { HostlessHaloTheme { OrganizerToolLinks(true) {} } }
        compose.onNodeWithText(briefTitle).assertExists()
        compose.onNodeWithText("World clocks").assertExists()
        compose.onNodeWithText("Sources").assertDoesNotExist()
        compose.onNodeWithText("Settings").assertDoesNotExist()
    }
}
