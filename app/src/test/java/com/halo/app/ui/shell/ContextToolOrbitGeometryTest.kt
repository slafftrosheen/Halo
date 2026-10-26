package com.halo.app.ui.shell

import org.junit.Assert.*
import org.junit.Test

/** Pure geometry sources; execution deferred until implementation is complete. */
class ContextToolOrbitGeometryTest {
    @Test fun leadingPaddingIsIncludedInSparseOrbitCoordinates() {
        assertEquals(108f, orbitItemCenter(0, 54, -81), 0f)
        assertEquals(81f, orbitItemCenter(0, 54, -54), 0f)
        assertEquals(135f, orbitItemCenter(54, 54, -54), 0f)
        assertEquals(0f, orbitItemTop(216f, orbitItemCenter(0, 54, -81), 112f, 88f, 48f), .001f)
    }

    @Test fun arcTargetsNeverIntersectTheFixedPttAtAnyDragPosition() {
        for (width in listOf(216f, 232f, 268f, 284f, 320f)) {
            val center = width / 2f
            val pttLeft = center - 48f
            val pttRight = center + 48f
            val pttTop = 68f
            for (step in 0..640) {
                val x = step * width / 640f
                val top = orbitItemTop(width, x, 112f, 88f, 48f)
                assertTrue("Touch target must fit scroll track", top >= 0f && top + 48f <= 136f)
                val horizontalOverlap = x + 24f > pttLeft && x - 24f < pttRight
                val verticalOverlap = top + 48f > pttTop && top < 164f
                assertFalse("Orbit intersects PTT at width=$width centerX=$x", horizontalOverlap && verticalOverlap)
            }
        }
    }

    @Test fun curveIsSymmetricAndRaisedOverTheOrb() {
        for (width in listOf(216f, 268f, 320f)) {
            assertEquals(0f, orbitItemTop(width, width / 2f, 112f, 88f, 48f), .001f)
            for (step in 0..20) {
                val x = step * width / 20f
                assertEquals(orbitItemTop(width, x, 112f, 88f, 48f),
                    orbitItemTop(width, width - x, 112f, 88f, 48f), .001f)
            }
        }
    }

    @Test fun curveTargetsStayClearOfTheExplicitFullListButton() {
        for (width in listOf(216f, 232f, 268f, 320f)) {
            for (step in 0..200) {
                val x = width * step / 200f
                val top = orbitItemTop(width, x, 112f, 88f, 48f)
                val horizontalOverlap = x + 24f > width - 48f && x - 24f < width
                assertFalse("Orbit must not cover list target", horizontalOverlap && top + 48f > 136f)
            }
        }
    }
}
