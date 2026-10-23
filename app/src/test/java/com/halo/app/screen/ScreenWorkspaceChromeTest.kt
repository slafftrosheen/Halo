package com.halo.app.screen

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ui.presence.*
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w700dp-h300dp")
class ScreenWorkspaceChromeTest {
    @get:Rule val compose = createComposeRule()
    private var notes = 0
    private var stops = 0
    private var tools = 0
    private var applies = 0
    private var cancels = 0
    private var exited = false

    private fun show(editing: Boolean = false, full: Boolean = true, active: Boolean = false, visible: Boolean = true, hasLayout: Boolean = true) {
        compose.setContent { HostlessHaloTheme(fontScale = 2f) { Box(Modifier.width(700.dp).height(300.dp)) {
            ScreenWorkspaceChrome(hasLayout, editing, full, visible, active, !active, {}, {}, { exited = !it }, {},
                { applies++ }, { cancels++ }, {}, {}, { notes++ }, {}, { size ->
                    HaloOrbLauncher(HaloSignalPresentation(reducedMotion = true),
                        HaloOrbInteraction(if (active) HaloOrbPrimary.STOP else HaloOrbPrimary.TALK, active),
                        HaloOrbShortcut.defaults, {}, { stops++ }, { stops++ }, { tools++ }, size)
                })
        } } }
    }

    @Test fun shortLargeTextDeskKeepsExitAndActiveStopWhileDecorativeChromeIsHidden() {
        show(active = true, visible = false)
        compose.onNodeWithText("Exit desk mode").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Stop").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, stops); assertEquals(0, tools); assertEquals(0, notes)
        compose.onNodeWithText("Exit desk mode").performClick()
        assertTrue(exited)
    }

    @Test fun noteDelegatesOnceAndLeavesDeskWithoutOpeningAnotherExecutor() {
        show()
        assertEquals(0, notes); assertEquals(0, stops); assertEquals(0, tools)
        compose.onNodeWithContentDescription("New note").assertIsDisplayed().performClick()
        assertEquals(1, notes); assertTrue(exited)
        assertEquals(0, stops); assertEquals(0, tools)
    }

    @Test fun previewControlsRequireExplicitApplyOrCancel() {
        show(editing = true, full = false)
        assertEquals(0, applies); assertEquals(0, cancels)
        compose.onNodeWithText("Apply").assertIsDisplayed().performClick()
        compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        assertEquals(1, applies); assertEquals(1, cancels)
        assertEquals(0, notes); assertEquals(0, tools)
    }

    @Test fun deskEntryBeforeAnyLayoutStillHasAnExplicitExit() {
        show(hasLayout = false, visible = false)
        compose.onNodeWithText("Exit desk mode").assertIsDisplayed().performClick()
        assertTrue(exited)
        compose.onNodeWithContentDescription("New note").assertDoesNotExist()
    }
}
