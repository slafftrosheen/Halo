package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.language.AppLanguage
import com.halo.app.ui.theme.HostlessHaloTheme as HaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class SettingsNavigationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun companionSettingsUseCanonicalDestinations() {
        val destinations = mutableListOf<SettingsDestination>()
        compose.setContent { HaloTheme(darkTheme = false) {
            SettingsContent(SettingsUiState(), AppLanguage.ENGLISH, {}, {}, { destinations += it })
        } }
        compose.onNodeWithText("How halo answers").performScrollTo().performClick()
        compose.onNodeWithText("Voice & reading").performScrollTo().performClick()
        assertEquals(listOf(SettingsDestination.INTELLIGENCE, SettingsDestination.VOICE), destinations)
        // Devices lives in primary navigation rather than a second Settings doorway.
        compose.onNodeWithText("Devices").assertDoesNotExist()
        compose.onAllNodesWithText("How halo answers").assertCountEquals(1)
        compose.onAllNodesWithText("Voice & reading").assertCountEquals(1)
    }
    @Test fun systemCheckIsARealDestinationAndNotAnImplicitProbe() {
        val destinations = mutableListOf<SettingsDestination>()
        compose.setContent { HaloTheme(darkTheme = true) {
            SettingsContent(SettingsUiState(), AppLanguage.ENGLISH, {}, {}, { destinations += it })
        } }
        compose.onNodeWithText("System check").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Check-up"))
        compose.onNodeWithText("Check-up").performClick()
        assertEquals(listOf(SettingsDestination.SYSTEM_CHECK), destinations)
        assertEquals("system_check", SettingsDestination.SYSTEM_CHECK.route)
    }

    @Test fun interfaceLanguageHasItsOwnSelectionCallback() {
        val languages = mutableListOf<AppLanguage>()
        compose.setContent { HaloTheme(darkTheme = false) {
            SettingsContent(SettingsUiState(), AppLanguage.RUSSIAN, { languages += it }, {}, {})
        } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Русский"))
        compose.onNodeWithText("Русский").performClick()
        compose.onNodeWithText("English").performClick()
        assertEquals(listOf(AppLanguage.ENGLISH), languages)
    }
}
