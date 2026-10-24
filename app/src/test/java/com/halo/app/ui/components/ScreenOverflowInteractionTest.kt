package com.halo.app.ui.components

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.halo.app.R
import com.halo.app.ui.screens.activity.ActivityOverflowMenu
import com.halo.app.ui.screens.devices.DevicesOverflowMenu
import com.halo.app.ui.screens.notebook.NotebookOverflowMenu
import com.halo.app.ui.screens.settings.SettingsOverflowMenu
import com.halo.app.ui.screens.study.StudyLessonOverflow
import com.halo.app.ui.screens.study.StudySetupOverflow
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.app.ui.workspace.StudioOverflowMenu
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w412dp-h900dp")
class ScreenOverflowInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun choose(label: String) {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.onNodeWithText(label).assertDoesNotExist()
    }

    @Test fun notebookRareActionsDispatchOnceAndDismissWithoutReplacingSearchEditor() {
        val called = mutableListOf<String>()
        compose.setContent { HostlessHaloTheme {
            NotebookOverflowMenu(3, { called += "chats" }, { called += "context" }, { called += "related" })
        } }
        choose("Saved chats")
        choose("In chat (3)")
        choose("Find related notes")
        assertEquals(listOf("chats", "context", "related"), called)
    }

    @Test fun deviceAudioTestAppearsOnlyForAnAvailableSystemAudioAccessory() {
        val audio = mutableStateOf(false)
        var details = 0
        var tests = 0
        compose.setContent { HostlessHaloTheme { DevicesOverflowMenu(audio.value, { details++ }, { tests++ }) } }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Test audio output").assertDoesNotExist()
        compose.onNodeWithText("This phone").performClick()
        assertEquals(1, details)
        compose.runOnIdle { audio.value = true }
        choose("Test audio output")
        assertEquals(1, tests)
    }

    @Test fun settingsMenuKeepsSetupAndAboutAsSeparateExistingCallbacks() {
        val called = mutableListOf<String>()
        compose.setContent { HostlessHaloTheme { SettingsOverflowMenu({ called += "setup" }, { called += "about" }) } }
        choose("Guided setup")
        choose("About halo")
        assertEquals(listOf("setup", "about"), called)
    }

    @Test fun activityDetailsOpensOnlyAfterExplicitSelection() {
        var details = 0
        compose.setContent { HostlessHaloTheme { ActivityOverflowMenu { details++ } } }
        compose.onNodeWithContentDescription("More options").performClick()
        assertEquals(0, details)
        compose.onNodeWithText("Details").performClick()
        assertEquals(1, details)
    }

    @Test fun studyPreservesSourceAndCompletionGuardsAndDeleteConfirmationRequest() {
        val open = mutableStateOf(false)
        val called = mutableListOf<String>()
        compose.setContent { HostlessHaloTheme {
            StudyLessonOverflow(true, open.value, { open.value = it }, false, false, true,
                { called += "details" }, { called += "listen" }, { called += "save" },
                { called += "finish" }, { called += "confirm-delete" })
        } }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Listen, then explain").assertIsNotEnabled()
        compose.onNodeWithText("Save study notes to Workspace").assertIsNotEnabled()
        compose.onNodeWithText("Already completed").assertIsNotEnabled()
        compose.onNodeWithText("Sources and progress").performClick()
        assertFalse(open.value)
        choose("Delete this session's data")
        assertEquals(listOf("details", "confirm-delete"), called)
    }

    @Test fun studySetupOptionalControlsToggleThroughExistingStateAndRespectBusy() {
        val options = mutableStateOf(false)
        val busy = mutableStateOf(false)
        compose.setContent { HostlessHaloTheme {
            StudySetupOverflow(options.value, !busy.value) { options.value = !options.value }
        } }
        choose("Age, language & learning options")
        assertTrue(options.value)
        choose("Hide learning options")
        assertFalse(options.value)
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithContentDescription("More options").assertIsNotEnabled()
    }

    @Test fun studioExportsRetainFormatAndBusyDismissesMenuWithoutRunningAnotherAction() {
        val busy = mutableStateOf(false)
        val dirty = mutableStateOf(false)
        val called = mutableListOf<String>()
        val app = ApplicationProvider.getApplicationContext<Application>()
        fun label(id: Int) = app.getString(id)
        compose.setContent { HostlessHaloTheme {
            StudioOverflowMenu(busy.value, dirty.value, { called += "background" }, { called += "presets" },
                { called += "copy" }, { called += "versions" }, { called += "discard" },
                { called += "PNG" }, { called += "JPEG" })
        } }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText(label(R.string.studio_discard)).assertIsNotEnabled()
        compose.onNodeWithText(label(R.string.studio_export_png)).performScrollTo().performClick()
        choose(label(R.string.studio_export_jpeg))
        choose(label(R.string.studio_save_as))
        choose(label(R.string.studio_versions))
        choose(label(R.string.studio_background))
        choose(label(R.string.studio_presets))
        compose.runOnIdle { dirty.value = true }
        choose(label(R.string.studio_discard))
        compose.onNodeWithContentDescription("More options").performClick()
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithText(label(R.string.studio_export_png)).assertDoesNotExist()
        compose.onNodeWithContentDescription("More options").assertIsNotEnabled()
        assertEquals(listOf("PNG", "JPEG", "copy", "versions", "background", "presets", "discard"), called)
    }
}
