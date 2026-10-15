package com.halo.app.ui.presence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloSharedOrbMotionTest {
    @Test fun sharedElementOnlyBetweenRoutesInOneActivity() {
        assertTrue(canShareHaloOrb(true, false))
        assertFalse(canShareHaloOrb(true, true))
        assertFalse(canShareHaloOrb(false, false))
    }
}
