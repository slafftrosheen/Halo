package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h780dp",
    shadows = [BrandBook3PowerManager::class])
class RoutineSuggestionVisualTest {
    @Test fun suggestionLight() = capture(false)
    @Test fun suggestionDark() = capture(true)
    @Test @Config(qualifiers = "ru-w360dp-h780dp")
    fun suggestionRussian() = capture(false)

    private fun capture(dark: Boolean) = captureRoboImage {
        HostlessHaloTheme(darkTheme = dark) {
            Surface(Modifier.size(360.dp, 780.dp), color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground) {
                val routine = RoutineTemplate.BATTERY.localizedDraft().routine
                RoutineSuggestionReview(RoutineSuggestion("visual", routine.id.value, 3), routine,
                    AutomationReadiness(), {}, {}, {}, {})
            }
        }
    }
}
