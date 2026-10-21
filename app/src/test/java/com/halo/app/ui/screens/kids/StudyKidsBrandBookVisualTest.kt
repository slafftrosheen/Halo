package com.halo.app.ui.screens.kids

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.screens.settings.BrandBook3PowerManager
import com.halo.app.ui.screens.study.*
import com.halo.app.ui.theme.HostlessHaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h780dp",
    shadows = [BrandBook3PowerManager::class])
class StudyKidsBrandBookVisualTest {
    @Before fun staticHostFrames() {
        val power = org.robolectric.RuntimeEnvironment.getApplication()
            .getSystemService(android.os.PowerManager::class.java)
        org.robolectric.Shadows.shadowOf(power).setIsPowerSaveMode(true)
    }
    @Test fun kidsLight360() = kidsCapture(false)
    @Test fun kidsDark360() = kidsCapture(true)
    @Test fun kidsLargeText360() = kidsCapture(false, 2f)
    @Test fun studyLight360() = studyCapture(false)
    @Test fun studyDark360() = studyCapture(true)
    @Test fun studyLargeText360() = studyCapture(false, 2f)
    @Test @Config(qualifiers = "en-w412dp-h892dp", shadows = [BrandBook3PowerManager::class])
    fun kidsLight412() = kidsCapture(false, width = 412, height = 892)
    @Test @Config(qualifiers = "en-w412dp-h892dp", shadows = [BrandBook3PowerManager::class])
    fun studyDark412() = studyCapture(true, width = 412, height = 892)

    private fun kidsCapture(dark: Boolean, scale: Float = 1f, width: Int = 360, height: Int = 780) = captureRoboImage {
        HostlessHaloTheme(darkTheme = dark, fontScale = scale) { KidsBrandTheme {
            Column(Modifier.width(width.dp).height(height.dp).background(MaterialTheme.colorScheme.background)) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    KidsHomeHero(HaloSignalPresentation(reducedMotion = true), {}, {}, {}, false)
                    KidsHomeChoices(recentLessonTitle = "Volcanoes", onSelect = {})
                }
                KidsVoiceDock("Talk or type", false, false, {}, {}, {}, {})
            }
        } }
    }

    private fun studyCapture(dark: Boolean, scale: Float = 1f, width: Int = 360, height: Int = 780) = captureRoboImage {
        HostlessHaloTheme(darkTheme = dark, fontScale = scale) {
            Column(Modifier.width(width.dp).height(height.dp).background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.halo.app.ui.design.HaloScreenHeader("Study", navigation = {})
                StudyReviewEntry(6, true, {})
                Text("PICK UP WHERE YOU LEFT OFF", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                StudyResumeCard("Photosynthesis", "Step 4 of 7 · Explore", {})
                StudyResumeCard("Seasons", "Step 2 of 7 · What do you already know?", {})
                androidx.compose.material3.FilledTonalButton({}, Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text("Start a new lesson")
                }
            }
        }
    }
}
