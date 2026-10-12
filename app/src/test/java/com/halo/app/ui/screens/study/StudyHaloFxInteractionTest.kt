package com.halo.app.ui.screens.study

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class StudyHaloFxInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun spotlightCallsOnlyTheActualAction() {
        var count = 0
        compose.setContent {
            HaloTheme(darkTheme = false) {
                StudyFocusAction(
                    text = "Start lesson",
                    glyph = HaloGlyph.PLAY,
                    enabled = true,
                    active = false, // Interaction contract; animation is checked on-device.
                    onClick = { count++ },
                )
            }
        }
        compose.onNodeWithText("Start lesson").performClick()
        compose.runOnIdle { assertEquals(1, count) }
    }

    @Test fun disabledSpotlightDoesNotAct() {
        var count = 0
        compose.setContent {
            HaloTheme(darkTheme = true) {
                StudyFocusAction(
                    text = "Start lesson",
                    glyph = HaloGlyph.PLAY,
                    enabled = false,
                    onClick = { count++ },
                )
            }
        }
        compose.onNodeWithText("Start lesson").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, count) }
    }

    @Test fun learningPathExplainsNextStepWithoutPretendingToMeasureMastery() {
        compose.setContent {
            HaloTheme(darkTheme = false) {
                StudySessionGuide(StudyUiState(loading = false))
            }
        }
        compose.onNodeWithText("Your learning path").assertExists()
        compose.onNodeWithText("Start with your own thoughts, or ask Halo for the first explanation.")
            .assertExists()
    }
}
