package com.halo.app.ui.screens.notebook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasArrangementMotionTest {
    @Test fun onlyApprovedAffectedNodesAnimate() {
        val affected = setOf("a", "b")
        assertTrue(canvasCardShouldGlide("a", affected, reducedMotion = false))
        assertTrue(canvasCardShouldGlide("b", affected, reducedMotion = false))
        assertFalse(canvasCardShouldGlide("c", affected, reducedMotion = false))
        assertFalse(canvasCardShouldGlide("a", affected, reducedMotion = true))
    }

    @Test fun noAnimationForManualOrUncommittedMovement() {
        assertFalse(canvasCardShouldGlide("a", emptySet(), reducedMotion = false))
    }
}
