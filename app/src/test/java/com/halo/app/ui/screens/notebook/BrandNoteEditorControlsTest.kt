package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BrandNoteEditorControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun modePickerHasExactlyThreeReachableChoices() {
        val choices = mutableListOf<EditorMode>()
        compose.setContent {
            HaloTheme(darkTheme = true) {
                var mode by remember { mutableStateOf(EditorMode.EDIT) }
                ModeToggle(mode) { value -> mode = value; choices += value }
            }
        }
        compose.onNodeWithText("Read").performClick()
        compose.onNodeWithText("Source").performClick()
        compose.onNodeWithText("Edit").performClick()
        assertEquals(listOf(EditorMode.READ, EditorMode.SOURCE, EditorMode.EDIT), choices)
    }

    @Test fun editorTitleAndRevisionRemainVisibleWithDirectRename() {
        var renameCalls = 0
        compose.setContent {
            HaloTheme {
                EditorWorkspaceHeader(
                    title = "Photosynthesis notes", expanded = false,
                    onToggle = {}, onBack = {}, onHalo = {},
                    subtitle = "Note · revision 7", onTitleClick = { renameCalls++ }
                )
            }
        }
        compose.onNodeWithText("Note · revision 7").assertExists()
        compose.onNodeWithText("Photosynthesis notes").performClick()
        assertEquals(1, renameCalls)
    }

    @Test fun busyEditorCannotOpenHaloFromHeader() {
        var calls = 0
        compose.setContent {
            HaloTheme {
                EditorWorkspaceHeader("Canvas", expanded = false,
                    onToggle = {}, onBack = {}, onHalo = { calls++ }, haloEnabled = false)
            }
        }
        compose.onNodeWithContentDescription("Think with halo").assertIsNotEnabled()
        assertEquals(0, calls)
    }

    @Test fun haloActionCannotExecuteWhenDisabled() {
        var calls = 0
        compose.setContent {
            HaloTheme {
                Column {
                    NoteHaloAction("Ask halo", HaloGlyph.THINK, enabled = false) { calls++ }
                    NoteHaloAction("Move to trash", HaloGlyph.TRASH, destructive = true) { calls++ }
                }
            }
        }
        compose.onNodeWithText("Ask halo").assertIsNotEnabled()
        compose.onNodeWithText("Move to trash").performClick()
        assertEquals(1, calls)
    }
}
