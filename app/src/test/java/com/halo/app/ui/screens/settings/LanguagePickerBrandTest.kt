package com.halo.app.ui.screens.settings

import com.halo.app.language.AppLanguage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagePickerBrandTest {
    @Test fun nativeAndTranslatedNamesAreSearchable() {
        assertTrue(languagePickerMatches(AppLanguage.LATVIAN, "Latvie"))
        assertTrue(languagePickerMatches(AppLanguage.LATVIAN, "Latvian"))
        assertTrue(languagePickerMatches(AppLanguage.RUSSIAN, "Рус"))
        assertTrue(languagePickerMatches(AppLanguage.RUSSIAN, "Russian"))
        assertTrue(languagePickerMatches(AppLanguage.ENGLISH, "en"))
        assertTrue(languagePickerMatches(AppLanguage.RUSSIAN, "Krievu", "Krievu"))
        assertFalse(languagePickerMatches(AppLanguage.ENGLISH, "Finnish"))
    }

    @Test fun blankQueryAlwaysShowsAllSupportedLanguages() {
        AppLanguage.entries.forEach {
            assertTrue(languagePickerMatches(it, "  "))
        }
    }
}
