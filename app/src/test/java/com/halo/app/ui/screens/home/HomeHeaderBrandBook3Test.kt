package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.halo.app.screen.HomeSurfaceSwitch
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h780dp")
class HomeHeaderBrandBook3Test {
    @get:Rule val compose = createComposeRule()

    @Test fun switchAndUpdatesShareOneHeaderWithFullTouchTargets() {
        var switched = false
        var updates = false
        compose.setContent {
            HostlessHaloTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.width(360.dp).testTag("header")) {
                        HomeBrandHeader({ updates = true }) {
                            HomeSurfaceSwitch(false, { switched = it })
                        }
                    }
                }
            }
        }
        assertTrue(compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot.height <= with(compose.density) { 64.dp.toPx() })
        compose.onNodeWithText("Screen").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Updates").performClick()
        assertTrue(switched)
        assertTrue(updates)
    }

    @Test fun enlargedTextKeepsBothSurfacesAndUpdatesReachable() {
        compose.setContent {
            HostlessHaloTheme(fontScale = 2f) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.width(360.dp).testTag("header")) {
                        HomeBrandHeader({}) { HomeSurfaceSwitch(false, {}) }
                    }
                }
            }
        }
        assertTrue(compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot.height <= with(compose.density) { 80.dp.toPx() })
        compose.onNodeWithText("Chat").assertIsDisplayed()
        compose.onNodeWithText("Screen").assertIsDisplayed()
        compose.onNodeWithContentDescription("Updates").assertIsDisplayed()
    }
}
