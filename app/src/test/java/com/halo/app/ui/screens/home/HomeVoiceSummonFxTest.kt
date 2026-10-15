package com.halo.app.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeVoiceSummonFxTest {
    @Test fun summonRequiresObservedMicOpenTransition() {
        assertFalse(shouldSummon(false, false))
        assertTrue(shouldSummon(false, true))
        assertFalse(shouldSummon(true, true))
        assertFalse(shouldSummon(true, false))
    }
}
