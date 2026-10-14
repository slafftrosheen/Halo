package com.halo.app.ui.presence

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.theme.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h1000dp")
class BrandPresenceVisualTest {
    private fun capture(dark: Boolean, kids: Boolean = false, accent: HaloAccent = HaloAccent.MINT) = captureRoboImage {
        HaloTheme(darkTheme = dark, palette = HaloPalette(accent, if (kids) HaloTone.WARM else HaloTone.NATURAL)) {
            Surface {
                Column(Modifier.width(360.dp).padding(8.dp)) {
                    PresenceArtwork.entries.chunked(3).forEach { row ->
                        Row {
                            row.forEach { art ->
                                Column(Modifier.width(114.dp)) {
                                    HaloSignalFace(HaloSignalPresentation(artwork = art, reducedMotion = true), size = 88.dp, kids = kids)
                                    Text(art.key, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    @Test fun lightStates() = capture(false)
    @Test fun darkStates() = capture(true)
    @Test fun kidsStates() = capture(false, true)
    @Test fun blueStates() = capture(true, accent = HaloAccent.BLUE)
    @Test fun animationMiddleFrames() = captureRoboImage {
        HaloTheme(darkTheme = true) {
            Surface {
                Column(Modifier.width(360.dp).padding(8.dp)) {
                    animatedArtwork.toList().chunked(3).forEach { row ->
                        Row {
                            row.forEach { art ->
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val composition = remember(art) {
                                    com.airbnb.lottie.LottieCompositionFactory.fromAssetSync(context,
                                        "halo/halo_orb_${art.key}_dark.json").value
                                }
                                Column(Modifier.width(114.dp)) {
                                    com.airbnb.lottie.compose.LottieAnimation(composition, progress = { .5f }, modifier = Modifier.size(88.dp))
                                    Text(art.key, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
