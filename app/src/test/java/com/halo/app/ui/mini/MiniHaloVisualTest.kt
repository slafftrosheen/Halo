package com.halo.app.ui.mini

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.theme.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h620dp")
class MiniHaloVisualTest {
    private fun capture(dark: Boolean, scale: Float = 0f) = captureRoboImage {
        HaloTheme(darkTheme = dark, fontScale = scale, palette = HaloPalette(HaloAccent.LILAC,HaloTone.COOL)) {
            Surface(Modifier.width(320.dp).height(620.dp)) {
                MiniHaloContent(HaloSignalPresentation(reducedMotion = true), "", {}, false, null, null,
                    {}, {}, {}, {}, {}, {})
            }
        }
    }
    @Test fun light() = capture(false)
    @Test fun dark() = capture(true)
    @Test @Config(qualifiers = "ru-rRU-w320dp-h620dp") fun russianLarge() = capture(false,2f)
    @Test @Config(qualifiers = "lv-rLV-w320dp-h620dp") fun latvianLarge() = capture(true,2f)
}
