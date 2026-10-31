package com.halo.app.ui.zone5

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class Zone5WorkspaceMenusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun menusAreReadOnlyUntilChosenAndTrackTheCurrentDocumentLease() {
        val holder = mutableStateOf<ShellPageProjection?>(null)
        val objectId = mutableStateOf("first")
        val calls = mutableListOf<String>()
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellPageActions provides holder) {
                BindShellPageActions(ShellPageOwner("studio", objectId.value), listOf(
                    ShellPageAction("page.studio.image", "Import picture", HaloGlyph.FILE) { calls += objectId.value },
                    ShellPageAction("page.studio.undo", "Undo", HaloGlyph.PREVIOUS, false) { error("Disabled action") }))
                Column { WorkspaceToolMenus() }
            }
        } }
        lateinit var stale: ShellPageProjection
        compose.runOnIdle { stale = holder.value!! }
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Import picture").assertIsDisplayed()
        assertTrue(calls.isEmpty())
        compose.runOnIdle { objectId.value = "second" }
        compose.onNodeWithText("Import picture").assertDoesNotExist()
        compose.runOnIdle { assertFalse(stale.invoke("page.studio.image")) }
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Import picture").performClick()
        assertEquals(listOf("second"), calls)
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Undo").assertIsNotEnabled()
    }
}
