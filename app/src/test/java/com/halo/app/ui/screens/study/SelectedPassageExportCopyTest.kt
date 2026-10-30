package com.halo.app.ui.screens.study

import com.halo.app.language.StudyJourneyCopy
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedPassageExportCopyTest {
    @Test fun selectedRangeExportUsesSupportedLanguageAndKeepsExactOffsets() {
        val locator = "Selected passage · characters 12–600"
        assertEquals("Selected passage · characters 12–600", StudyJourneyCopy.translate(locator, "en"))
        assertEquals("Выбранный отрывок · символы 12–600", StudyJourneyCopy.translate(locator, "ru-RU"))
        assertEquals("Atlasītais fragments · rakstzīmes 12–600", StudyJourneyCopy.translate(locator, "lv"))
        assertEquals("Zaznaczony fragment · znaki 12–600", StudyJourneyCopy.translate(locator, "pl"))
        assertEquals("Selected passage · characters 12–600", locator)
    }

    @Test fun ordinarySourceTextDoesNotBecomeTranslatedMetadata() {
        val text = "Copper is the selected word."
        assertEquals(text, StudyJourneyCopy.translate(text, "ru"))
        val nonCanonical = "Before Selected passage · characters 12–600 after"
        assertEquals(nonCanonical, StudyJourneyCopy.translate(nonCanonical, "lv"))
    }
}
