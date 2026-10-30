package com.halo.app.ui.presence

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class)
class HaloOrbThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun zoneOrbAndControlsFollowAccentAndToneChanges() = verify(zone5 = true)
    @Test fun legacyAndReducedMotionOrbsFollowTheSameTheme() = verify(zone5 = false)

    private fun verify(zone5: Boolean) {
        val palette = mutableStateOf(HaloPalette(accent = HaloAccent.BLUE, tone = HaloTone.COOL))
        val dark = mutableStateOf(false)
        val state = mutableStateOf(HaloSignalState.IDLE)
        var primary = Color.Unspecified
        var background = Color.Unspecified
        compose.setContent { HostlessHaloTheme(darkTheme = dark.value, palette = palette.value) {
            val scheme = MaterialTheme.colorScheme
            SideEffect { primary = scheme.primary; background = scheme.background }
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize().testTag("theme_surface")) {
                Column {
                    HaloSignalFace(HaloSignalPresentation(state = state.value, label = "Current state", reducedMotion = true),
                        modifier = Modifier.testTag("theme_orb"), size = if (zone5) 72.dp else 160.dp, zone5 = zone5)
                    Button({}, modifier = Modifier.testTag("theme_control")) { Text("Action") }
                }
            }
        } }
        for ((accent, tone, night) in listOf(Triple(HaloAccent.BLUE, HaloTone.COOL, false),
            Triple(HaloAccent.ROSE, HaloTone.WARM, false), Triple(HaloAccent.SAGE, HaloTone.SLATE, true))) {
            compose.runOnIdle { palette.value = HaloPalette(accent = accent, tone = tone); dark.value = night }
            for (phase in listOf(HaloSignalState.IDLE, HaloSignalState.CAPTURING, HaloSignalState.THINKING, HaloSignalState.SPEAKING)) {
                compose.runOnIdle { state.value = phase }
                assertPaintContains("theme_orb", primary, "$accent/$tone/$phase")
            }
            assertPaintContains("theme_control", primary, "themed control")
            assertPaintContains("theme_surface", background, "themed surface")
        }
    }

    private fun assertPaintContains(tag: String, expected: Color, context: String) {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        var found = false
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val actual = pixels[x, y]
            if (kotlin.math.abs(actual.red - expected.red) < .015f && kotlin.math.abs(actual.green - expected.green) < .015f &&
                kotlin.math.abs(actual.blue - expected.blue) < .015f) found = true
        }
        assertTrue("$context must paint the current theme role", found)
    }
}
