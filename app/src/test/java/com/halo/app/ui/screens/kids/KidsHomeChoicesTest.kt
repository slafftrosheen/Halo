package com.halo.app.ui.screens.kids

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
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
class KidsHomeChoicesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allThreeHomeChoicesNavigateOnlyWhenTapped() {
        val calls = mutableListOf<KidsDestination>()
        compose.setContent {
            HaloTheme {
                KidsBrandTheme {
                    KidsHomeChoices(onSelect = { calls += it })
                }
            }
        }
        compose.onNodeWithText("Ask me anything").performClick()
        compose.onNodeWithText("Learn").performClick()
        compose.onNodeWithText("Create").performClick()
        assertEquals(
            listOf(KidsDestination.ASK, KidsDestination.LEARN, KidsDestination.CREATE),
            calls,
        )
    }

    @Test fun storyCannotSubmitBeforeIdeaIsEntered() {
        var started = 0
        compose.setContent {
            HaloTheme {
                KidsBrandTheme {
                    var idea by remember { mutableStateOf("") }
                    KidsCreationPanel(
                        idea, { idea = it }, enabled = true,
                        onCreate = { started++ },
                    )
                }
            }
        }
        compose.onNodeWithText("Start creating").assertIsNotEnabled()
        compose.onNodeWithText("Write a story").performClick()
        compose.onNodeWithText("Start creating").assertIsEnabled().performClick()
        assertEquals(1, started)
    }

    @Test fun busyStoryHasNoSubmissionAction() {
        compose.setContent {
            HaloTheme {
                KidsBrandTheme {
                    KidsCreationPanel(
                        idea = "A friendly planet", onIdeaChange = {},
                        enabled = false, onCreate = { error("Busy creation was submitted") },
                    )
                }
            }
        }
        compose.onNodeWithText("Start creating").assertIsNotEnabled()
    }
}
