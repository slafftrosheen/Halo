package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CanvasEmptyStarterTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyBoardOffersThreeActionsThatCallTheirRealOwners() {
        val calls = mutableListOf<String>()
        compose.setContent {
            HaloTheme(darkTheme = true) {
                CanvasEmptyStarter(
                    onText = { calls += "text" },
                    onImport = { calls += "import" },
                    onHalo = { calls += "halo" }
                )
            }
        }
        compose.onNodeWithText("Add text card").performClick()
        compose.onNodeWithText("Bring in a note or file").performClick()
        compose.onNodeWithText("Think with halo").performClick()
        assertEquals(listOf("text", "import", "halo"), calls)
    }

    @Test fun emptyBoardDoesNotOfferActionsDuringMutation() {
        val disabled = mutableStateOf(true)
        compose.setContent {
            HaloTheme(darkTheme = false) {
                CanvasEmptyStarter(
                    enabled = disabled.value.not(),
                    onText = {}, onImport = {}, onHalo = {}
                )
            }
        }
        compose.onNodeWithText("Add text card").assertIsNotEnabled()
        compose.onNodeWithText("Bring in a note or file").assertIsNotEnabled()
        compose.onNodeWithText("Think with halo").assertIsNotEnabled()
    }
}
