package com.halo.app.ui.screens.notebook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasDraftDrawOnFxTest {
    @Test fun onlyNewGeneratedDraftCanUseTheOneShot() {
        assertFalse(canvasDraftDrawOnShouldPlay(0, 0, false))
        assertFalse(canvasDraftDrawOnShouldPlay(1, 1, true))
        assertFalse(canvasDraftDrawOnShouldPlay(1, 2, false))
        assertTrue(canvasDraftDrawOnShouldPlay(1, 2, true))
    }

    @Test fun editingNeverAdvancesTheGeneratedDraftRevision() {
        assertFalse(canvasDraftDrawOnShouldPlay(8, 8, true))
    }
}
