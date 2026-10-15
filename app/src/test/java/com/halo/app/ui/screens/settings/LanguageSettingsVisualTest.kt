package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.language.AppLanguage
import com.halo.app.ui.theme.HaloTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class LanguageSettingsVisualTest {
    @org.junit.Before fun useStillMotionForCapture() {
        android.provider.Settings.Global.putFloat(org.robolectric.RuntimeEnvironment.getApplication().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test fun compactLight() = capture(false)
    @Test fun compactDark() = capture(true)

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = "ru-rRU-w320dp-h800dp")
    fun russianLargeText() = capture(true, 1.5f)

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = "lv-rLV-w320dp-h800dp")
    fun latvianLargeText() = capture(false, 1.5f)

    private fun capture(dark: Boolean, scale: Float = 1f) {
        captureRoboImage {
            HaloTheme(darkTheme = dark, fontScale = scale) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.size(320.dp, 740.dp)) {
                        LanguageSettingsScreen(AppLanguage.LATVIAN, {}, {})
                    }
                }
            }
        }
    }
}
