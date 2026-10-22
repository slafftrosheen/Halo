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
import com.halo.app.screen.HomeSurfaceSwitch
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Host rendering evidence only; this fixture does not qualify device/runtime integration. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class HomeChatVisualTest {
    @Test fun lightThread360() = capture(false)
    @Test @Config(qualifiers = "w412dp-h900dp") fun darkThread412() = capture(true, width = 412)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun russianLargeText() = capture(false, 2f)
    @Test @Config(qualifiers = "lv-rLV-w360dp-h800dp") fun latvianLargeText() = capture(true, 2f)
    @Test @Config(qualifiers = "w640dp-h360dp") fun narrowLandscape() = capture(false, width = 560, height = 180)
    @Test @Config(qualifiers = "w800dp-h1000dp") fun tabletThread() = capture(true, width = 760, height = 900)

    private fun capture(dark: Boolean, scale: Float = 1f, width: Int = 360, height: Int = 740) = captureRoboImage {
        HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.width(width.dp).height(height.dp)) {
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        HomeChatHeader(uiText("Chat"), true, {}, {}, {}, {}, {}, {},
                            surfaceSwitcher = { HomeSurfaceSwitch(false, {}, compact = scale >= 1.5f) })
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeTranscriptMessage("question", "Help me plan a quiet afternoon.", user = true, onEdit = {})
                        HomeTranscriptMessage("answer", "Start with one important task, then leave time for a walk.", user = false)
                    }
                    Box(Modifier.padding(12.dp)) {
                        HaloComposer("", {}, {}, {}, {}, true, true, false, uiText("Talk"), false,
                            onAttach = {}, onTools = {})
                    }
                }
            }
        }
    }
}
