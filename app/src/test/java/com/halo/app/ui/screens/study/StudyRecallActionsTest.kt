package com.halo.app.ui.screens.study

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class StudyRecallActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bothHonestRatingsCallOnlyTheirOwnHandler() {
        var notYet = 0
        var know = 0
        compose.setContent {
            HaloTheme {
                StudyRecallActions(
                    enabled = true,
                    onNotYet = { notYet++ },
                    onKnow = { know++ },
                )
            }
        }
        compose.onNodeWithText("Not yet").performClick()
        assertEquals(1, notYet)
        assertEquals(0, know)
        compose.onNodeWithText("I know it").performClick()
        assertEquals(1, notYet)
        assertEquals(1, know)
    }

    @Test fun reviewInProgressBlocksBothRatings() {
        compose.setContent {
            HaloTheme {
                StudyRecallActions(
                    enabled = false,
                    onNotYet = { error("Disabled review recorded a failure") },
                    onKnow = { error("Disabled review recorded a success") },
                )
            }
        }
        compose.onNodeWithText("Not yet").assertIsNotEnabled()
        compose.onNodeWithText("I know it").assertIsNotEnabled()
    }
}
