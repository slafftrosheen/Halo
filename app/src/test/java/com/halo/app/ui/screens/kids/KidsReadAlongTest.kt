package com.halo.app.ui.screens.kids

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KidsReadAlongTest {
    @Test fun followsExactTtsCharacterRangeAndNothingElse() {
        val sentence = "The quick fox"
        assertEquals(4..8, kidsTtsHighlightRange(sentence, 4..8))
        assertEquals(0..2, kidsTtsHighlightRange(sentence, 0..2))
    }

    @Test fun ignoresStaleOrInvalidRangeInsteadOfGuessing() {
        assertNull(kidsTtsHighlightRange("Hello", null))
        assertNull(kidsTtsHighlightRange("Hello", -1..2))
        assertNull(kidsTtsHighlightRange("Hello", 1..10))
        assertNull(kidsTtsHighlightRange("", 0..1))
    }
}
