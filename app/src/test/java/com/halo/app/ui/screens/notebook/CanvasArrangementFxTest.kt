package com.halo.app.ui.screens.notebook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasArrangementFxTest {
    @Test fun onlyActualNewOrganizationWritesPlayArrange() {
        assertFalse(canvasArrangeShouldPlay(0L, 0L))
        assertTrue(canvasArrangeShouldPlay(0L, 1L))
        assertTrue(canvasArrangeShouldPlay(5L, 6L))
        assertFalse(canvasArrangeShouldPlay(6L, 6L))
        assertFalse(canvasArrangeShouldPlay(6L, 5L))
    }
}
