package com.halo.app.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.language.uiText
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class HomeRoundTwoVisualTest {
    @Test fun lightHome() = capture(false)
    @Test fun darkHome() = capture(true)
    @Test @Config(qualifiers = "ru-rRU-w320dp-h800dp")
    fun russianLargeText() = capture(false, 2f, 320)
    @Test @Config(qualifiers = "lv-rLV-w320dp-h800dp")
    fun latvianLargeText() = capture(true, 2f, 320)

    private fun capture(dark: Boolean, scale: Float = 1f, width: Int = 360) = captureRoboImage {
        HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.width(width.dp).height(740.dp).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        HomeBrandHeader({})
                        HomePresenceHeader(HaloSignalPresentation(reducedMotion = true), uiText("Ready when you are"))
                        HomeWelcome(compact = true, onDraft = {})
                    }
                    HaloComposer("", {}, {}, {}, {}, true, true, false, uiText("Talk"), false)
                }
            }
        }
    }
}
