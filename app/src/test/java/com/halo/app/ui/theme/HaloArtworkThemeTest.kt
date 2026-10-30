package com.halo.app.ui.theme

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.compose.LottieAnimation
import com.halo.app.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class)
class HaloArtworkThemeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun zoneShapeAnimationRepaintsWhenThePaletteChanges() = assertMotion(rawRes = R.raw.halo5_motion_orb_listening_dark)

    @Test fun legacyGradientAnimationRepaintsWhenThePaletteChanges() = assertMotion(asset = "halo/halo_orb_thinking_dark.json")

    private fun assertMotion(asset: String? = null, rawRes: Int? = null) {
        val accent = mutableStateOf(HaloAccent.BLUE)
        var expected = Color.Unspecified
        var ready: LottieComposition? = null
        compose.setContent { HostlessHaloTheme(darkTheme = true, palette = HaloPalette(accent = accent.value)) {
            val primary = MaterialTheme.colorScheme.primary
            val composition = rememberHaloThemedLottie(asset, rawRes)
            SideEffect { expected = primary; ready = composition }
            if (composition != null) LottieAnimation(composition, progress = { .5f },
                modifier = Modifier.size(160.dp).testTag("themed_motion"))
        } }
        var previous: HaloAccent? = null
        for (value in listOf(HaloAccent.BLUE, HaloAccent.ROSE, HaloAccent.SAGE)) {
            val old = ready
            compose.runOnIdle { accent.value = value }
            // LottieTask completes through Android's main Handler. A variable-only
            // wait does not drain Robolectric's paused looper like a semantics query does.
            compose.waitUntil(10_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                compose.waitForIdle()
                ready != null && (previous == null || ready !== old)
            }
            previous = value
            val pixels = compose.onNodeWithTag("themed_motion").captureToImage().toPixelMap()
            var found = false
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (kotlin.math.abs(pixel.red - expected.red) < .02f && kotlin.math.abs(pixel.green - expected.green) < .02f &&
                    kotlin.math.abs(pixel.blue - expected.blue) < .02f && pixel.alpha > .95f) found = true
            }
            assertTrue("The rendered animation must use $value", found)
        }
    }

    @Test fun gradientRecoloringPreservesStopsAndTheirOpacityPositions() {
        val source = """{"layers":[{"shapes":[{"ty":"gf","g":{"p":3,"k":{"a":0,"k":[0,0.1,0.9,0.7,0.55,0.1,0.9,0.7,1,0.1,0.9,0.7,0,0.28,0.55,0.22,1,0]}},"o":{"a":0,"k":70}}]}]}"""
        val colors = HaloArtworkColors(haloColorScheme(true, HaloPalette(HaloAccent.BLUE)))
        val shape = org.json.JSONObject(colors.lottie(source)).getJSONArray("layers").getJSONObject(0).getJSONArray("shapes").getJSONObject(0)
        val gradient = shape.getJSONObject("g")
        val stops = gradient.getJSONObject("k").getJSONArray("k")
        assertEquals(3, gradient.getInt("p")); assertEquals(18, stops.length())
        assertEquals(0.55, stops.getDouble(4), 0.0); assertEquals(1.0, stops.getDouble(8), 0.0)
        assertEquals(listOf(0.0,0.28,0.55,0.22,1.0,0.0), (12..17).map { stops.getDouble(it) })
        assertEquals(70, shape.getJSONObject("o").getInt("k"))
    }

    @Test fun staticSvgRecolorsPaintWithoutChangingOpacityOrGeometry() {
        val scheme = haloColorScheme(true, HaloPalette(HaloAccent.BLUE, HaloTone.WARM))
        val source = """<svg><path d="M0 0L12 12" stroke="rgb(12,242,186)" fill="#060330" opacity=".4"/></svg>"""
        val themed = HaloArtworkColors(scheme).svg(source)
        assertTrue(themed.contains("stroke=\"rgb(142,197,255)\""))
        assertTrue(themed.contains("fill=\"#160e1c\""))
        assertTrue(themed.contains("d=\"M0 0L12 12\""))
        assertTrue(themed.contains("opacity=\".4\""))
    }

    @Test fun authoredAnimationsRetainTheirTimingAndParseAfterRecoloring() {
        val mapper = HaloArtworkColors(haloColorScheme(false, HaloPalette(HaloAccent.ROSE, HaloTone.PAPER)))
        fun verify(source: String, name: String) {
            val before = org.json.JSONObject(source)
            if (!before.has("layers")) return
            val themed = mapper.lottie(source)
            val after = org.json.JSONObject(themed)
            for (field in listOf("fr", "ip", "op", "w", "h")) assertEquals("$name/$field", before.getDouble(field), after.getDouble(field), 0.0)
            val result = LottieCompositionFactory.fromJsonStringSync(themed, null)
            assertNotNull("$name: ${result.exception}", result.value)
        }
        fun visit(path: String) {
            for (child in context.assets.list(path).orEmpty()) {
                val name = "$path/$child"
                if (child.endsWith(".json")) context.assets.open(name).bufferedReader().use { verify(it.readText(), name) }
                else if (!child.contains('.')) visit(name)
            }
        }
        visit("halo")
        for (id in listOf(R.raw.halo5_motion_orb_listening_dark, R.raw.halo5_motion_orb_thinking_light, R.raw.halo5_motion_orb_speaking_dark)) {
            context.resources.openRawResource(id).bufferedReader().use { verify(it.readText(), id.toString()) }
        }
    }
}
