package com.halo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceDraftTest {
    @Test fun recognisedWordsEnterTheDraftWithItsConfiguredSeparator() {
        assertEquals("Existing draft\nSpoken text", dictationDraft("Existing draft ", "Spoken text", true, "\n", 100))
        assertEquals("Spoken text", dictationDraft("Prior draft", "Spoken text", false, " ", 100))
        assertEquals("Spoken text", dictationDraft(" ", "Spoken text", true, " ", 100))
        assertNull(dictationDraft("Existing draft", "Spoken text", true, " ", 15))
    }
}
