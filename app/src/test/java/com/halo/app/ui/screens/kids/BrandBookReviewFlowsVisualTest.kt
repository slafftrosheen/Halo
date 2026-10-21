package com.halo.app.ui.screens.kids

import android.app.Application
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.screens.settings.BrandBook3PowerManager
import com.halo.app.ui.screens.study.StudyReviewQuestionFace
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
class BrandBookReviewFlowsVisualTest {
    @Before fun staticFrames() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getSystemService(PowerManager::class.java))
            .setIsPowerSaveMode(true)
    }
    @Test fun draftLight360() = draft(false)
    @Test fun draftDark360() = draft(true)
    @Test fun draftLargeText360() = draft(false, 2f)
    @Test fun reviewFront360() = face(null)
    @Test fun reviewBack360() = face("I ate")
    @Test @Config(qualifiers = "en-w412dp-h892dp", shadows = [BrandBook3PowerManager::class])
    fun storyLargeText412() = captureRoboImage {
        HostlessHaloTheme(fontScale = 2f) { KidsBrandTheme { Frame(412, 892) {
            KidsStoryPage(AnnotatedString("One night a little star fell into the snow. Pip ran to help it."),
                large = true, spacing = true, onWordClick = {})
        } } }
    }
    private fun draft(dark: Boolean, scale: Float = 1f) = captureRoboImage {
        HostlessHaloTheme(darkTheme = dark, fontScale = scale) { KidsBrandTheme { Frame {
            KidsProjectReview(KidsProjectState(loading = false, idea = "Fox and the Lost Star",
                draft = "In a snowy forest lived a brave fox called Pip. One night a little star fell into the snow…",
                editing = true), false, {}, {}, {}, {})
        } } }
    }
    private fun face(answer: String?) = captureRoboImage {
        HostlessHaloTheme { Frame { StudyReviewQuestionFace("comí", answer) } }
    }
    @Composable private fun Frame(width: Int = 360, height: Int = 780, content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.width(width.dp).height(height.dp).background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
