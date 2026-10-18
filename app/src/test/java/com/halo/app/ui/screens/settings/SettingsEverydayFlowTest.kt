package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.language.AppLanguage
import com.halo.app.ui.theme.AppearanceMode
import com.halo.app.ui.theme.HaloTheme
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
            SettingsContent(SettingsUiState(), AppLanguage.ENGLISH, {}, {}, { opened += it })
        } }
        compose.onNodeWithText("Runtime status").assertDoesNotExist()
        compose.onNodeWithText("System check").assertDoesNotExist()
        compose.onNodeWithText("Vision Lab").assertDoesNotExist()
        compose.onNodeWithText("Advanced").performScrollTo().performClick()
        assertTrue(opened.isEmpty())
        compose.onNodeWithText("Vision Lab").performScrollTo().performClick()
        assertEquals(listOf(SettingsDestination.VISION_LAB), opened)
        compose.onNodeWithText("Everyday").performScrollTo().performClick()
        compose.onNodeWithText("Vision Lab").assertDoesNotExist()
        compose.onNodeWithText("Intelligence").performScrollTo().assertIsDisplayed()
    }

    @Test fun appearanceOpensWithoutChangingThemeAndSelectionIsExplicit() {
        val choices = mutableListOf<AppearanceMode>()
        compose.setContent { HaloTheme {
            SettingsContent(SettingsUiState(), AppLanguage.ENGLISH, {}, { choices += it }, {})
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
        compose.onNodeWithText("Search Advanced settings").performScrollTo().performClick()
        compose.onNodeWithText("Runtime status").performScrollTo().assertIsDisplayed()
        assertTrue(opened.isEmpty())
    }

    @Test fun advancedDeepLinkLandsOnTechnicalTab() {
        compose.setContent { HaloTheme {
            SettingsContent(SettingsUiState(), AppLanguage.ENGLISH, {}, {}, {}, initialAdvanced = true)
        } }
        compose.onNodeWithText("Runtime status").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Intelligence").assertDoesNotExist()
    }
}
