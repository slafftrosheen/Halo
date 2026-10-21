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
import org.junit.Assert.assertTrue
import androidx.compose.ui.graphics.luminance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h780dp",
    shadows = [BrandBook3PowerManager::class])
class StudyKidsBrandBookTest {
    @get:Rule val compose = createComposeRule()

    @Test fun darkKidsBodyHasReadableForegroundOutsideCards() {
        compose.setContent {
            HostlessHaloTheme(darkTheme = true) { KidsBrandTheme { Text("Contrast sample") } }
        }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Contrast sample").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().layoutInput.style.color.luminance() > .8f)
    }

    @Test fun liveDockStopsWithoutStartingAnotherCapture() {
        var stops = 0
        var captures = 0
        compose.setContent {
            HostlessHaloTheme { KidsBrandTheme {
                KidsVoiceDock("Voice input", busy = true, saving = false,
                    onSpeak = { captures++ }, onType = {}, onStop = { stops++ }, onHelp = {})
            } }
        }
        compose.onNodeWithContentDescription("Stop").assertHeightIsAtLeast(72.dp).performClick()
        assertEquals(1, stops)
        assertEquals(0, captures)
        compose.onNodeWithContentDescription("Type instead").assertHeightIsAtLeast(56.dp)
    }

    @Test fun enlargedResumeHasOneAccessibleContinueTarget() {
        var continued = 0
        compose.setContent {
            HostlessHaloTheme(fontScale = 2f) {
                Column(Modifier.width(320.dp)) {
                    StudyResumeCard("Photosynthesis", "Step 3 of 7", { continued++ })
                }
            }
        }
        compose.onNodeWithText("Continue").assertIsDisplayed().performClick()
        assertEquals(1, continued)
    }

}
