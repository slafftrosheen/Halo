package com.halo.app.ui.screens.study

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.app.ui.screens.settings.BrandBook3PowerManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h780dp", shadows = [BrandBook3PowerManager::class])
class StudyRecallVisualTest {
    @Test fun light() = capture(false, 1f)
    @Test fun dark() = capture(true, 1f)
    @Test fun largeText() = capture(false, 2f)
    private fun capture(dark: Boolean, scale: Float) = captureRoboImage {
        HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
            Surface(Modifier.width(360.dp).height(400.dp), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("How well did you remember?", style = MaterialTheme.typography.titleMedium)
                    StudyRecallActions(true, {}, {}, onAlmost = {})
                }
            }
        }
    }
}
