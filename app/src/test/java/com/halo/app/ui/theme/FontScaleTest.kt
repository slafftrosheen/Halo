package com.halo.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class FontScaleTest {
    @Test fun appPreferenceCannotShrinkAccessibilityText() {
        assertEquals(2f, effectiveFontScale(2f, 0.45f))
        assertEquals(2f, effectiveFontScale(2f, 1.5f))
    }

    @Test fun systemDefaultAndLargerAppPreferenceAreRespected() {
        assertEquals(1.2f, effectiveFontScale(1.2f, 0f))
        assertEquals(1.5f, effectiveFontScale(1f, 1.5f))
        assertEquals(1.2f, effectiveFontScale(1.2f, Float.NaN))
    }
}
