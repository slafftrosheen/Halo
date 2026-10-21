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

    @Test fun allThreeRatingsCallOnlyTheirOwnHandler() {
        var notYet = 0
        var know = 0
        var almost = 0
        compose.setContent {
            HaloTheme(fontScale = 2f) {
                StudyRecallActions(
                    enabled = true,
                    onNotYet = { notYet++ },
                    onKnow = { know++ },
                    onAlmost = { almost++ },
                )
            }
        }
        compose.onNodeWithText("Again").assertIsDisplayed().performClick()
        assertEquals(1, notYet)
        assertEquals(0, know)
        compose.onNodeWithText("I knew it").assertIsDisplayed().performClick()
        assertEquals(1, notYet)
        assertEquals(1, know)
        compose.onNodeWithText("Almost").assertIsDisplayed().performClick()
        assertEquals(1, almost)
        assertEquals(1, know)
        assertEquals(1, notYet)
    }

    @Test fun reviewInProgressBlocksAllRatings() {
        compose.setContent {
            HaloTheme {
                StudyRecallActions(
                    enabled = false,
                    onNotYet = { error("Disabled review recorded a failure") },
                    onKnow = { error("Disabled review recorded a success") },
                    onAlmost = { error("Disabled review recorded partial recall") },
                )
            }
        }
        compose.onNodeWithText("Almost").assertIsNotEnabled()
        compose.onNodeWithText("Again").assertIsNotEnabled()
        compose.onNodeWithText("I knew it").assertIsNotEnabled()
    }
}
