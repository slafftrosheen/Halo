package com.halo.app.ui.screens.kids

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsResponsiveCardsTest {
    @Test fun narrowScreenOrLargeTextStacksChoiceCards() {
        assertTrue(kidsHomeChoicesStacked(2.0f, 320))
        assertTrue(kidsHomeChoicesStacked(1.5f, 393))
        assertTrue(kidsHomeChoicesStacked(1.0f, 300))
    }

    @Test fun ordinaryPhoneStacksTranslatedLabels() {
        assertTrue(kidsHomeChoicesStacked(1.0f, 393))
        assertTrue(kidsHomeChoicesStacked(1.2f, 360))
    }

    @Test fun wideDisplayRetainsTwoEqualChoices() {
        assertFalse(kidsHomeChoicesStacked(1.0f, 480))
        assertFalse(kidsHomeChoicesStacked(1.2f, 600))
    }
}
