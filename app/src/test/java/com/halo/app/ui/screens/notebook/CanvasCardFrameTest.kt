package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CanvasCardFrameTest {
    @get:Rule val compose = createComposeRule()

    @Test fun zoomScalesContentAndHitFrameTogether() {
        compose.setContent {
            CanvasCardFrame(200f, 100f, 0.5f, Modifier.testTag("frame")) {
                Box(Modifier.fillMaxSize().background(Color.Red).testTag("content"))
            }
        }
        compose.onNodeWithTag("frame").assertWidthIsEqualTo(100.dp).assertHeightIsEqualTo(50.dp)
        // Layout remains in board coordinates; boundsInRoot includes the visual transform.
        val bounds = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        with(compose.density) {
            assertEquals(100.dp.toPx(), bounds.width, 0.5f)
            assertEquals(50.dp.toPx(), bounds.height, 0.5f)
        }
    }
}
