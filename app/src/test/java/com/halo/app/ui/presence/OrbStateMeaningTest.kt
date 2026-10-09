package com.halo.app.ui.presence

import com.halo.app.ui.icons.HaloGlyph
import org.junit.Assert.*
import org.junit.Test

class OrbStateMeaningTest {
    @Test fun terminalAndActiveStatesCannotFallBackToTheReadyIcon() {
        HaloSignalState.entries.filter { it != HaloSignalState.IDLE }.forEach { state ->
            assertNotEquals(state.name, HaloGlyph.HALO, HaloSignalPresentation(state = state).orbGlyph())
        }
        // Older assistant callers supplied an offline glyph on an error; the state must win.
        assertEquals(HaloGlyph.ERROR, HaloSignalPresentation(HaloSignalState.ERROR, HaloGlyph.OFFLINE).orbGlyph())
    }
    @Test fun preparationAndWaitingKeepTheirActualMeaning() {
        assertNotEquals(HaloGlyph.APPROVAL, HaloSignalPresentation(HaloSignalState.WAITING, HaloGlyph.SEE).orbGlyph())
        listOf(HaloGlyph.QUESTION, HaloGlyph.DEVICE, HaloGlyph.STORAGE, HaloGlyph.PAUSE).forEach { glyph ->
            assertEquals(glyph, HaloSignalPresentation(HaloSignalState.WAITING, glyph).orbGlyph())
        }
    }
    @Test fun recognizingAndPlanningRemainDistinguishableFromReasoning() {
        val thinking = HaloSignalPresentation(HaloSignalState.THINKING, HaloGlyph.THINK).orbGlyph()
        val recognizing = HaloSignalPresentation(HaloSignalState.THINKING, HaloGlyph.LISTEN).orbGlyph()
        val planning = HaloSignalPresentation(HaloSignalState.THINKING, HaloGlyph.ACTION).orbGlyph()
        assertEquals(3, setOf(thinking, recognizing, planning).size)
    }
}
