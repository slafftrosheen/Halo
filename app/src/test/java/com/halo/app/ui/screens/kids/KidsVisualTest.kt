package com.halo.app.ui.screens.kids

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.language.uiText
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.theme.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w320dp-h800dp")
class KidsVisualTest {
    private fun capture(dark: Boolean, scale: Float = 0f) = captureRoboImage {
        HaloTheme(darkTheme = dark, fontScale = scale, palette = HaloPalette(HaloAccent.LILAC, HaloTone.COOL)) {
            KidsBrandTheme {
            Column(Modifier.width(320.dp).height(800.dp).background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                KidsModeHeader(HaloSignalPresentation(reducedMotion = true), {}, {}, {}, {})
                KidsCard {
                    Text(uiText("My learning adventure"), style = MaterialTheme.typography.titleLarge)
                    KidsLearningStart("", {}, false, {})
                }
                KidsCard { Text(uiText("Ask Halo"), style = MaterialTheme.typography.titleLarge); Text(uiText("My tools")) }
            }
            }
        }
    }
    @Test fun light() = capture(false)
    @Test fun dark() = capture(true)
    @Test @Config(qualifiers = "ru-rRU-w320dp-h800dp") fun russianLargeText() = capture(false, 2f)
    @Test @Config(qualifiers = "lv-rLV-w320dp-h800dp") fun latvianLargeText() = capture(true, 2f)
}
