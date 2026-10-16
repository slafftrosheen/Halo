package com.halo.app.ui.screens.study

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.halo.app.ui.theme.HaloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class StudyReviewQuestionFaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsOnlySavedQuestionAndHonestQuestionLabel() {
        compose.setContent {
            HaloTheme(darkTheme = true) {
                StudyReviewQuestionFace("Why does light bend through glass?")
            }
        }
        compose.onNodeWithText("Question").assertExists()
        compose.onNodeWithText("Why does light bend through glass?").assertExists()
    }
}
