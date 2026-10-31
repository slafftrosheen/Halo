package com.halo.app.ui.presence

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieAnimation
import com.halo.app.ui.theme.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android renderer, synthetic states and fixed animation frames. No microphone or provider. */
@RunWith(AndroidJUnit4::class)
class Zone5OrbStyleDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun classicOrbFramesFollowTheThemeAtBothZoneSizes() {
        val accent = mutableStateOf(HaloAccent.BLUE)
        val dark = mutableStateOf(true)
        val ready = mutableStateMapOf<String, LottieComposition?>()
        var ink = Color.Unspecified
        val states = listOf(HaloSignalState.IDLE, HaloSignalState.CAPTURING, HaloSignalState.THINKING, HaloSignalState.SPEAKING)
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                HaloTheme(darkTheme = dark.value, palette = HaloPalette(accent.value)) {
                    val currentInk = MaterialTheme.colorScheme.primary
                    SideEffect { ink = currentInk }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Classic Halo orb", style = MaterialTheme.typography.headlineMedium)
                            Text("Static 48dp / animated 64dp / animated 72dp")
                            states.forEach { state ->
                                val signal = HaloSignalPresentation(state = state, label = state.name)
                                Text(state.name, style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    HaloSignalFace(signal.copy(reducedMotion = true), size = 48.dp, zone5 = true)
                                    for (size in listOf(64, 72)) {
                                        val path = requireNotNull(zone5OrbMotionAsset(signal, size.toFloat(), dark.value, true))
                                        val composition = rememberHaloThemedLottie(asset = path)
                                        val key = "${state.name}-$size"
                                        SideEffect { ready[key] = composition }
                                        if (composition != null) LottieAnimation(composition, progress = { .5f },
                                            modifier = Modifier.size(size.dp).testTag(key))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        var first = true
        for ((value, night) in listOf(HaloAccent.BLUE to true, HaloAccent.MINT to true, HaloAccent.ROSE to false)) {
            val old = ready.toMap()
            compose.runOnIdle { accent.value = value; dark.value = night }
            compose.waitUntil(15_000) {
                ready.size == 8 && ready.values.all { it != null } &&
                    (first || ready.all { (key, composition) -> composition !== old[key] })
            }
            first = false
            for (state in states) for (size in listOf(64, 72)) {
                val pixels = compose.onNodeWithTag("${state.name}-$size").assertIsDisplayed().captureToImage().toPixelMap()
                var painted = 0
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val actual = pixels[x,y]
                    if (kotlin.math.abs(actual.red - ink.red) < .025f && kotlin.math.abs(actual.green - ink.green) < .025f &&
                        kotlin.math.abs(actual.blue - ink.blue) < .025f && actual.alpha > .95f) painted++
                }
                assertTrue("$value/$state/$size must paint its themed thick ring", painted > 40)
                val composition = ready["${state.name}-$size"]!!
                assertEquals(240, composition.bounds.width())
                assertTrue("The classic halo glow must be present", composition.layers.any { it.name == "glow" })
                assertTrue("The thick ring must remain present", composition.layers.any { it.name == "ring" })
            }
            val output = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "zone5-orb-polish").apply { mkdirs() }
            File(output, "classic-${value.name.lowercase()}.png").outputStream().use {
                compose.onNode(isRoot()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
