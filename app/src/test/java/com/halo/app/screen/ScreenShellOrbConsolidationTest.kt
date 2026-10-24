package com.halo.app.screen

import android.app.Application
import androidx.compose.material3.Text
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
class ScreenShellOrbConsolidationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shellHidesLocalOrbAndFallbackTalkButKeepsLayoutApplyAndCancel() {
        var applies = 0
        var cancels = 0
        val withOrb = mutableStateOf(true)
        compose.setContent {
            HostlessHaloTheme {
                CompositionLocalProvider(LocalShellHaloOrb provides true) {
                    ScreenWorkspaceChrome(true, true, false, true, false, true,
                        {}, {}, {}, {}, { applies++ }, { cancels++ }, {}, {}, {}, {},
                        orbControl = if (withOrb.value) ({ Text("Local Screen orb") }) else null)
                }
            }
        }
        compose.onNodeWithText("Local Screen orb").assertDoesNotExist()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, applies)
        assertEquals(1, cancels)
        compose.runOnIdle { withOrb.value = false }
        compose.onNodeWithContentDescription("Talk to halo").assertDoesNotExist()
    }
}
