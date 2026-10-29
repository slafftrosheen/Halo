package com.halo.app.ui.theme

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Pixel behavior only: host captures do not establish cached draws or physical CPU/frame cost. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class HaloAmbientLayerRenderingTest {
    @get:Rule val compose = createComposeRule()
    private val phase = mutableFloatStateOf(0f)
    private val base = Color(0xff15221e)

    @Test fun haloFragmentsVisiblyMoveByLayerTranslationAndHoldAtTheSamePhase() = checkMotion(HaloBackgroundStyle.HALO)
    @Test fun mistGradientsVisiblyMoveByLayerTranslationAndHoldAtTheSamePhase() = checkMotion(HaloBackgroundStyle.MIST)

    private fun checkMotion(style: HaloBackgroundStyle) {
        compose.setContent {
            HaloAmbientBackdropLayers(Modifier.size(240.dp, 320.dp).testTag("backdrop"), style, base, Color.White, phase, false)
        }
        val surface = compose.onNodeWithTag("backdrop")
        surface.assert(hasClickAction().not())
        val before = surface.captureToImage()
        compose.runOnIdle { phase.floatValue = 1.2f }
        val after = surface.captureToImage()
        assertTrue("Motif translation must change visible pixels", pixelsDiffer(before, after))
        assertFalse("A held phase must keep the same image", pixelsDiffer(after, surface.captureToImage()))
    }

    @Test fun stillRendersOnlyTheUnmodifiedBase() = checkStatic(HaloBackgroundStyle.STILL, false)
    @Test fun highContrastRendersOnlyTheUnmodifiedBase() = checkStatic(HaloBackgroundStyle.HALO, true)

    private fun checkStatic(style: HaloBackgroundStyle, contrast: Boolean) {
        compose.setContent {
            HaloAmbientBackdropLayers(Modifier.size(240.dp, 320.dp).testTag("backdrop"), style, base, Color.White, phase, contrast)
        }
        val pixels = compose.onNodeWithTag("backdrop").captureToImage().toPixelMap()
        for (y in 0 until pixels.height step 8) for (x in 0 until pixels.width step 8) {
            assertEquals(base.toArgb(), pixels[x, y].toArgb())
        }
    }

    private fun pixelsDiffer(first: ImageBitmap, second: ImageBitmap): Boolean {
        assertEquals(first.width, second.width); assertEquals(first.height, second.height)
        val a = first.toPixelMap(); val b = second.toPixelMap()
        return (0 until a.height).any { y -> (0 until a.width).any { x -> a[x, y].toArgb() != b[x, y].toArgb() } }
    }

}
