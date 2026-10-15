package com.halo.app.ui.fx

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloAccentSweepTest {
    @Test fun onlyPaletteChangesTriggerSweep() {
        val before = Color.Red to Color.Blue
        val after = Color.Green to Color.Blue
        assertFalse(haloAccentSweepChanged(before, before))
        assertTrue(haloAccentSweepChanged(before, after))
        assertTrue(haloAccentSweepChanged(after, before))
    }
}
