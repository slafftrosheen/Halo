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

    @Test fun ordinaryPhoneRetainsTwoEqualChoices() {
        assertFalse(kidsHomeChoicesStacked(1.0f, 393))
        assertFalse(kidsHomeChoicesStacked(1.2f, 360))
    }
}
