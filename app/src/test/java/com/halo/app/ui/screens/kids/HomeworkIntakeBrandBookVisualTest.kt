package com.halo.app.ui.screens.kids

import android.app.Application
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.screens.settings.BrandBook3PowerManager
import com.halo.app.ui.screens.study.HomeworkWordingReview
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h780dp",
    shadows = [BrandBook3PowerManager::class])
class HomeworkIntakeBrandBookVisualTest {
    @Before fun staticFrames() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getSystemService(PowerManager::class.java))
            .setIsPowerSaveMode(true)
    }
    @Test fun wordingStudyLight360() = wording(false, false)
    @Test fun wordingKidsLight360() = wording(true, false)
    @Test fun wordingKidsLargeText360() = wording(true, false, 2f)
    @Test @Config(qualifiers = "en-w412dp-h892dp", shadows = [BrandBook3PowerManager::class])
    fun wordingKidsDark412() = wording(true, true, width = 412, height = 892)
    @Test fun unreadablePhoto360() = captureRoboImage {
        HostlessHaloTheme { KidsBrandTheme {
            Box(Modifier.width(360.dp).height(780.dp).background(MaterialTheme.colorScheme.background)) {
                HomeworkPhotoReviewContent(null, true, false, {}, {}, {})
            }
        } }
    }
    private fun wording(kids: Boolean, dark: Boolean, scale: Float = 1f,
        width: Int = 360, height: Int = 780) = captureRoboImage {
        HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
            val content: @androidx.compose.runtime.Composable () -> Unit = {
                Column(Modifier.width(width.dp).height(height.dp).background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState()).padding(16.dp)) {
                    HomeworkWordingReview("A box is 12 cm long and 7 cm wide. What is its area?",
                        "Check the width against your photo before starting.", kids, true, {}, {})
                }
            }
            if (kids) KidsBrandTheme(content) else content()
        }
    }
}
