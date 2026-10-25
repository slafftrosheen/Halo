package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class CanvasShellAssistantEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shellOwnedDockOmitsLocalOrbAndRetainsAllSixEditingCallbacks() {
        val calls = mutableListOf<String>()
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellHaloOrb provides true) {
                CanvasBrandDock(true, false, { calls += "select" }, { calls += "text" },
                    { calls += "sketch" }, { calls += "link" }, { calls += "group" },
                    { calls += "import" }, { calls += "halo" })
            }
        } }
        compose.onAllNodesWithContentDescription("Think with halo", useUnmergedTree = true).assertCountEquals(0)
        listOf("Select", "Text", "Sketch", "Link", "Group", "Import").forEach { label ->
            compose.onNodeWithContentDescription(label).performScrollTo().performClick()
        }
        assertEquals(listOf("select", "text", "sketch", "link", "group", "import"), calls)
    }

    @Test fun standaloneDockKeepsOneActionableOrbAndRemovesItWhenShellTakesOwnership() {
        val shellOwned = mutableStateOf(false)
        var halo = 0
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellHaloOrb provides shellOwned.value) {
                CanvasBrandDock(true, true, {}, {}, {}, {}, {}, {}, { halo++ })
            }
        } }
        compose.onAllNodesWithContentDescription("Think with halo", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithContentDescription("Think with halo").performClick()
        assertEquals(1, halo)
        compose.runOnIdle { shellOwned.value = true }
        compose.onAllNodesWithContentDescription("Think with halo", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Done selecting").assertExists()
        assertEquals(1, halo)
    }

    @Test fun shellOwnedEmptyStarterKeepsCreationAndImportWithoutAnotherAssistantEntry() {
        val calls = mutableListOf<String>()
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellHaloOrb provides true) {
                CanvasEmptyStarter(onText = { calls += "text" }, onImport = { calls += "import" }, onHalo = { calls += "halo" })
            }
        } }
        compose.onAllNodesWithContentDescription("Think with halo", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("Think with halo").assertDoesNotExist()
        compose.onNodeWithText("Add text card").performClick()
        compose.onNodeWithText("Bring in a note or file").performClick()
        assertEquals(listOf("text", "import"), calls)
    }
}
