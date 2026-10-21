package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.language.AppLanguage
import com.halo.app.ui.theme.AppearanceMode
import com.halo.app.ui.theme.HostlessHaloTheme as HaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class SettingsEverydayFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun developerToolsStayInAdvancedAndDoNotRunOnEntry() {
        val opened = mutableListOf<SettingsDestination>()
        compose.setContent { HaloTheme {
            SettingsContent(SettingsUiState(), AppLanguage.ENGLISH, {}, {}, { opened += it }, initialAdvanced = true)
        } }
        compose.onNodeWithText("Runtime status").assertDoesNotExist()
        compose.onNodeWithText("System check").assertDoesNotExist()
        compose.onNodeWithText("Vision Lab").assertDoesNotExist()
        compose.onNodeWithContentDescription("Developer tools").performScrollTo().performClick()
        compose.onNodeWithText("Turn on").performClick()
        assertTrue(opened.isEmpty())
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Vision Lab"))
        compose.onNodeWithText("Vision Lab").performClick()
        assertEquals(listOf(SettingsDestination.VISION_LAB), opened)
        compose.onNodeWithText("How halo answers").assertDoesNotExist()
    }

    @Test fun appearanceOpensWithoutChangingThemeAndSelectionIsExplicit() {
        val choices = mutableListOf<AppearanceMode>()
        compose.setContent { HaloTheme {
            SettingsContent(SettingsUiState(), AppLanguage.ENGLISH, {}, { choices += it }, {},
                palettePreferences = com.halo.app.ui.theme.PalettePreferences(androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()),
                assistantPreferences = com.halo.app.ui.theme.AssistantPreferences(androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()))
        } }
        compose.onNodeWithText("Accent colour").assertDoesNotExist()
        compose.onNodeWithText("Appearance").performScrollTo().performClick()
        assertTrue(choices.isEmpty())
        compose.onNodeWithText("Dark").performClick()
        assertEquals(listOf(AppearanceMode.DARK), choices)
    }

    @Test fun searchCanHandOffToAdvancedWithoutRunningAnything() {
        val opened = mutableListOf<SettingsDestination>()
        compose.setContent { HaloTheme {
            SettingsContent(SettingsUiState(), AppLanguage.ENGLISH, {}, {}, { opened += it })
        } }
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("runtime")
        compose.onNodeWithText("Runtime status").assertDoesNotExist()
        compose.onNodeWithText("Open Advanced").performScrollTo().performClick()
        assertEquals(listOf(SettingsDestination.ADVANCED), opened)
    }

    @Test fun advancedDeepLinkLandsOnTechnicalTab() {
        compose.setContent { HaloTheme {
            SettingsContent(SettingsUiState(), AppLanguage.ENGLISH, {}, {}, {}, initialAdvanced = true)
        } }
        compose.onNodeWithText("Model roles").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Runtime status").assertDoesNotExist()
        compose.onNodeWithContentDescription("Developer tools").performScrollTo().assertIsOff()
        compose.onNodeWithText("Intelligence").assertDoesNotExist()
    }
}
