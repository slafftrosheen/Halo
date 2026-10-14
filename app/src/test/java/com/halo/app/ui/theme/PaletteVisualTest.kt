package com.halo.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.screens.settings.PaletteControls
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w320dp-h1000dp")
class PaletteVisualTest {
    private fun capture(dark: Boolean, scale: Float = 0f) {
        val preferences = PalettePreferences(RuntimeEnvironment.getApplication())
        captureRoboImage {
            HaloTheme(darkTheme = dark, fontScale = scale) {
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    Column(Modifier.width(320.dp).height(1000.dp)
                        .verticalScroll(rememberScrollState())) { PaletteControls(preferences) }
                }
            }
        }
    }
    @Test fun lightControls() = capture(false)
    @Test fun darkControls() = capture(true)
    @Test @Config(qualifiers = "ru-rRU-w320dp-h1000dp") fun russianLargeControls() = capture(false, 2f)
}
