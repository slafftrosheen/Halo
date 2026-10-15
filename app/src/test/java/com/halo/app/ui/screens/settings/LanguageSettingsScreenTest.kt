package com.halo.app.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.language.AppLanguage
import com.halo.app.ui.theme.HaloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w320dp-h800dp")
class LanguageSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchMatchesNativeNamesAndShowsAnEmptyStateWithoutSelecting() {
        val selected = mutableListOf<AppLanguage>()
        compose.setContent { HaloTheme(darkTheme = true) {
            LanguageSettingsScreen(AppLanguage.ENGLISH, { selected += it }, {})
        } }
        compose.onNode(hasSetTextAction()).performTextReplacement("  РУС  ")
        compose.onNodeWithText("Русский").assertExists()
        compose.onNodeWithText("Latviešu").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextReplacement("unknown")
        compose.onNodeWithText("No languages found.").assertExists()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Latviešu").assertExists()
        assertEquals(emptyList<AppLanguage>(), selected)
    }

    @Test fun selectionRemainsVisibleAndBackDoesNotChangeIt() {
        val selected = mutableListOf<AppLanguage>()
        var backs = 0
        compose.setContent {
            var language by remember { mutableStateOf(AppLanguage.ENGLISH) }
            HaloTheme(darkTheme = false) {
                LanguageSettingsScreen(language, { language = it; selected += it }, { backs++ })
            }
        }
        compose.onNodeWithText("Latviešu").performClick()
        compose.onNode(isSelected() and hasText("Latviešu")).assertExists()
        compose.onNodeWithText("Search languages").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf(AppLanguage.LATVIAN), selected)
        assertEquals(1, backs)
    }
}
