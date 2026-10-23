package com.halo.app.screen

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w700dp-h300dp")
class ScreenAppearanceSheetTest {
    @get:Rule val compose = createComposeRule()
    @Test fun shortSheetPreserves200PercentTextAndPinnedDoneWhileOptionsScroll() {
        var appearance by mutableStateOf(ScreenAppearance())
        var scale = 0f
        var closed = false
        compose.setContent { HostlessHaloTheme(fontScale = 2f) {
            ScreenNativeSheet({ closed = true }) {
                scale = LocalDensity.current.fontScale
                ScreenSheetBody("Screen appearance", { closed = true }) {
                    ScreenAppearanceControls(appearance) { appearance = it }
                }
            }
        } }
        compose.runOnIdle { assertEquals(2f, scale, 0f) }
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Sora").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ScreenFont.SORA, appearance.font) }
        compose.onNodeWithText("Mint").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ScreenBackground.MINT, appearance.background) }
        compose.onAllNodes(isRoot()).onLast().captureRoboImage()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
}
