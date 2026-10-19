package com.halo.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.glass.components.HaloGlassPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassSettingRow
import com.halo.app.ui.glass.components.HaloGlassTextField
import com.halo.app.ui.theme.HostlessHaloTheme as HaloTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Secondary screens are covered by the same visual language as Home and Notebook. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class SecondaryBrandVisualTest {
    @Test fun compactLightSettings() = capture(false, 320)
    @Test fun compactDarkSettings() = capture(true, 320)

    @Test
    @Config(qualifiers = "w412dp-h900dp")
    fun normalWidthLightSettings() = capture(false, 412)

    private fun capture(dark: Boolean, width: Int) {
        captureRoboImage {
            HaloTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.width(width.dp).height(740.dp)) {
                        HaloGlassPage(title = "Voice & intelligence") {
                            HaloGlassSection(title = "Provider") {
                                HaloGlassSettingRow(title = "Model location",
                                    value = "On device", onClick = {})
                                HaloGlassTextField(
                                    value = "Private notes stay here",
                                    onValueChange = {},
                                    label = "Preview an instruction",
                                    placeholder = "Ask Halo…",
                                )
                            }
                            HaloGlassSection(title = "Permissions") {
                                HaloGlassSettingRow(title = "Microphone",
                                    value = "Only when listening", onClick = {})
                            }
                        }
                    }
                }
            }
        }
    }
}
