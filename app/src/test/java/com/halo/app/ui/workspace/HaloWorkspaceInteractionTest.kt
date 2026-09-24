package com.halo.app.ui.workspace

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.halo.app.ui.theme.HaloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HaloWorkspaceInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disabledRowExposesNoClickAction() {
        composeRule.setContent {
            HaloTheme(darkTheme = false) {
                Column {
                    HaloWorkspaceRow(
                        title = "Unavailable action",
                        enabled = false,
                        onClick = {}
                    )
                }
            }
        }

        composeRule
            .onNodeWithText("Unavailable action")
            .assertIsNotEnabled()
            .assertHasNoClickAction()
    }

    @Test
    fun selectedInteractiveRowExposesSelectionAndClick() {
        composeRule.setContent {
            HaloTheme(darkTheme = false) {
                Column {
                    HaloWorkspaceRow(
                        title = "Selected action",
                        selected = true,
                        onClick = {}
                    )
                }
            }
        }

        composeRule
            .onNodeWithText("Selected action")
            .assertIsSelected()
            .assertHasClickAction()
    }

    @Test
    fun disabledButtonIsNotEnabled() {
        composeRule.setContent {
            HaloTheme(darkTheme = false) {
                HaloWorkspaceButton(
                    text = "Disabled button",
                    onClick = {},
                    enabled = false
                )
            }
        }

        composeRule
            .onNodeWithText("Disabled button")
            .assertIsNotEnabled()
    }
}
