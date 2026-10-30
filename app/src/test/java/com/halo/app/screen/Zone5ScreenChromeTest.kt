package com.halo.app.screen

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
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
class Zone5ScreenChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hostedEditModeNamesThePreviewAndKeepsApplyAndCancelExplicit() {
        var applied = 0; var cancelled = 0
        compose.setContent { HostlessHaloTheme {
            CompositionLocalProvider(LocalShellHaloOrb provides true) {
                ScreenWorkspaceChrome(true, true, false, true, false, true,
                    {}, {}, {}, {}, { applied++ }, { cancelled++ }, {}, {}, {}, {}, null, shellZone = true)
            }
        } }
        compose.onNodeWithText("Preview").assertIsDisplayed()
        compose.onNodeWithText("Home").assertDoesNotExist()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, applied); assertEquals(1, cancelled)
    }
}
