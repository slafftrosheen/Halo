package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w800dp-h360dp-land")
class EditorWorkspaceChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun toolsLeaveRoomAndPreserveDraftWhenToggled() {
        compose.setContent {
            HaloTheme {
                var expanded by remember { mutableStateOf(false) }
                var draft by remember { mutableStateOf("Saved draft") }
                Column(Modifier.size(800.dp, 360.dp)) {
                    EditorWorkspaceHeader("Notebook", expanded, { expanded = !expanded }, {})
                    if (expanded) EditorToolsPanel {
                        repeat(20) { Text("Editor action $it") }
                    }
                    OutlinedTextField(draft, { draft = it },
                        Modifier.weight(1f).fillMaxWidth().testTag("workspace"))
                }
            }
        }
        val initial = compose.onNodeWithTag("workspace").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithText("Editor action 0").assertDoesNotExist()
        compose.onNodeWithTag("workspace").performTextReplacement("Draft stays editable")
        compose.onNodeWithContentDescription("Tools").performClick()
        compose.onNodeWithText("Editor action 0").assertExists()
        val open = compose.onNodeWithTag("workspace").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Open tools retain at least half the editing space", open >= initial * 0.5f)
        compose.onNodeWithContentDescription("Tools").performClick()
        compose.onNodeWithText("Draft stays editable").assertExists()
        compose.onNodeWithText("Editor action 0").assertDoesNotExist()
        val closed = compose.onNodeWithTag("workspace").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Closing restores the workspace", closed >= initial - 1f)
    }
}
