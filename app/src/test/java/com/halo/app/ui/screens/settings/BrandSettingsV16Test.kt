package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.language.AppLanguage
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BrandSettingsV16Test {
    @get:Rule val compose = createComposeRule()

    @Test fun searchFiltersDestinationsWithoutExecutingThem() {
        val opened = mutableListOf<SettingsDestination>()
        compose.setContent {
            HaloTheme(darkTheme = true) {
                SettingsContent(SettingsUiState(), AppLanguage.ENGLISH, {}, {}, { opened += it })
            }
        }
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("privacy")
        compose.onNodeWithText("Intelligence").assertDoesNotExist()
        compose.onNodeWithText("Context & privacy").performScrollTo().performClick()
        assertEquals(listOf(SettingsDestination.PRIVACY), opened)
    }

    @Test fun categorySearchShowsChildrenAndUnknownQueryExplainsNoResults() {
        compose.setContent {
            HaloTheme {
                SettingsContent(SettingsUiState(), AppLanguage.ENGLISH, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Actions & connections")
        compose.onNodeWithText("Integrations").assertExists()
        compose.onNodeWithText("Skills").assertExists()
        compose.onNodeWithText("Automation").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("zzzxxy")
        compose.onNodeWithText("No matching settings").assertExists()
    }

    @Test fun pureSearchIsCaseInsensitiveAndDoesNotForceEmptyResults() {
        assertTrue(settingsMatches("", "Intelligence"))
        assertTrue(settingsMatches("VOICE", "Voice"))
        assertFalse(settingsMatches("camera", "Privacy", "Voice"))
        assertTrue(settingsHasMatches("runtime"))
        assertFalse(settingsHasMatches("totally unknown feature"))
    }

    @Test fun locationCardNavigatesWithoutChangingAnyProvider() {
        var opens = 0
        compose.setContent {
            HaloTheme(darkTheme = true) {
                IntelligenceLocationCard(
                    title = "On this phone",
                    subtitle = "Private and offline with an installed model",
                    glyph = HaloGlyph.LOCAL, current = false, recommended = true,
                    onOpen = { opens++ }
                )
            }
        }
        compose.onNodeWithText("On this phone").performClick()
        assertEquals(1, opens)
        compose.onNodeWithText("Recommended").assertExists()
        compose.onNodeWithText("Current location").assertDoesNotExist()
    }
}
