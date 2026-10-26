package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.language.AppLanguage
import com.halo.app.ui.theme.AppearanceMode
import com.halo.app.ui.theme.AppearancePreferences
import com.halo.app.ui.theme.PalettePreferences
import com.halo.app.ui.theme.AssistantPreferences
import com.halo.app.ui.theme.HostlessHaloTheme as HaloTheme
import kotlinx.coroutines.runBlocking
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

    @Test fun appearanceOpensWithoutChangingThemeAndSelectionIsExplicit() = runBlocking {
        val choices = mutableListOf<AppearanceMode>()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val palettes = PalettePreferences(context)
        val appearance = AppearancePreferences(context)
        val assistant = AssistantPreferences(context)
        palettes.awaitReady()
        appearance.setModePersisted(AppearanceMode.LIGHT)
        compose.setContent { HaloTheme {
            val mode by appearance.mode.collectAsState()
            SettingsContent(SettingsUiState(appearanceMode = mode), AppLanguage.ENGLISH, {}, { choices += it }, {},
                palettePreferences = palettes, assistantPreferences = assistant,
                onApplyAppearance = { draft ->
                    appearance.applyAppearancePersisted(draft.savedMode, draft.mode,
                        palettes, draft.savedPalettes, draft.palettes)
                    choices += draft.mode
                })
        } }
        compose.onNodeWithText("Accent colour").assertDoesNotExist()
        compose.onNodeWithText("Appearance").performScrollTo().performClick()
        assertTrue(choices.isEmpty())
        compose.onNodeWithText("Dark").performClick()
        assertTrue("Selecting a mode must remain a local preview", choices.isEmpty())
        assertEquals(AppearanceMode.LIGHT, appearance.mode.value)
        compose.onNode(hasText("Cancel") and hasClickAction()).performScrollTo().performClick()
        assertTrue("Cancel must not persist the preview", choices.isEmpty())
        assertEquals(AppearanceMode.LIGHT, appearance.mode.value)
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        assertTrue(choices.isEmpty())
        compose.onNode(hasText("Apply") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5000) { choices.isNotEmpty() }
        assertEquals(listOf(AppearanceMode.DARK), choices)
        val restored = AppearancePreferences(context)
        restored.awaitLoaded()
        assertEquals("Explicit Apply must survive reopening the preference owner", AppearanceMode.DARK, restored.mode.value)
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
