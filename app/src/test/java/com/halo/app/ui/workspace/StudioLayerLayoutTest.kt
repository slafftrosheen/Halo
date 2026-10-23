package com.halo.app.ui.workspace

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.data.studio.*
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
class StudioLayerLayoutTest {
    @get:Rule val compose = createComposeRule()
    private fun render(dark: Boolean, scale: Float, apply: String, close: String, file: String) {
        var applied: StudioLayer? = null
        var dismissed = false
        val layer = StudioLayer(id = "layer", name = "Local artwork", kind = StudioLayerKind.TEXT, text = "A small idea")
        compose.setContent { HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
            StudioLayerDialog(layer, { applied = it }, { dismissed = true })
        } }
        compose.onNodeWithText(apply).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription(close).assertIsDisplayed()
        val output = File("build/outputs/issue51/$file.png").also { it.parentFile.mkdirs() }
        compose.onNodeWithTag("studio-dialog").captureRoboImage(output)
        compose.onNodeWithText(apply).performClick()
        assertEquals(layer, applied)
        assertTrue(dismissed)
    }
    @Test fun lightPhone360() = render(false, 1f, "Apply", "Close", "layer-light-360")
    @Test @Config(qualifiers = "ru-rRU-w740dp-h360dp-land")
    fun russianShortLandscape200() = render(true, 2f, "Применить", "Закрыть", "layer-dark-ru-landscape-200")
    @Test @Config(qualifiers = "lv-rLV-w412dp-h900dp")
    fun latvianLargePhone200() = render(false, 2f, "Lietot", "Aizvērt", "layer-light-lv-412-200")
}
