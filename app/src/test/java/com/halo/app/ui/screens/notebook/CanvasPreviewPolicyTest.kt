package com.halo.app.ui.screens.notebook

import org.junit.Assert.*
import org.junit.Test

class CanvasPreviewPolicyTest {
    @Test fun overlappingLargeProjectIsBoundedAndExplicitSelectionWins() {
        val cards = (0 until 500).map { CanvasPreviewCandidate("card-$it", 10f, 10f, 200f, 180f, it) }
        val admitted = admittedCanvasPreviews(cards, 400f, 800f, "card-0")
        assertEquals(16, admitted.size)
        assertTrue("card-0" in admitted)
        assertTrue("card-499" in admitted)
        assertEquals(admitted, admittedCanvasPreviews(cards.reversed(), 400f, 800f, "card-0"))
    }

    @Test fun panningReleasesOldSourcesAndDoesNotAdmitInvalidOrOffscreenCards() {
        val cards = listOf(CanvasPreviewCandidate("old", -250f, 10f, 200f, 180f, 0),
            CanvasPreviewCandidate("current", 10f, 10f, 200f, 180f, 1),
            CanvasPreviewCandidate("nan", Float.NaN, 10f, 200f, 180f, 2),
            CanvasPreviewCandidate("empty", 10f, 10f, 0f, 180f, 3))
        assertEquals(setOf("current"), admittedCanvasPreviews(cards, 400f, 800f, "old"))
        assertTrue(admittedCanvasPreviews(cards, 0f, 800f, null).isEmpty())
    }

    @Test fun distantSourcesWaitUntilTheyBecomeVisibleAndDuplicateIdsCannotSpendTheBudget() {
        val visible = CanvasPreviewCandidate("one", -20f, -20f, 40f, 40f, 0)
        assertEquals(setOf("one"), admittedCanvasPreviews(List(100) { visible } +
            CanvasPreviewCandidate("far", 9000f, 9000f, 400f, 400f, 1), 400f, 800f, null))
    }

    @Test fun narrowAndWideImagesCannotBePannedCompletelyOutOfView() {
        val tall = transformCanvasInspection(CanvasInspectionTransform(), 400f, 800f, .05f,
            zoom = 4f, panX = 10000f, panY = 10000f)
        assertEquals(4f, tall.scale, .001f)
        assertEquals(0f, tall.x, .001f)
        assertEquals(1200f, tall.y, .001f)
        val wide = transformCanvasInspection(CanvasInspectionTransform(), 400f, 800f, 20f,
            zoom = 4f, panX = -10000f, panY = -10000f)
        assertEquals(-600f, wide.x, .001f)
        assertEquals(0f, wide.y, .001f)
        val fit = transformCanvasInspection(wide, 400f, 800f, 20f, zoom = .01f)
        assertEquals(CanvasInspectionTransform(), fit)
    }

    @Test fun zoomHonorsTheGestureAnchorAndRejectsInvalidInputs() {
        val next = transformCanvasInspection(CanvasInspectionTransform(), 400f, 400f, 1f,
            zoom = 2f, anchorX = 50f)
        assertEquals(-50f, next.x, .001f)
        assertEquals(CanvasInspectionTransform(), transformCanvasInspection(next, 0f, 400f, 1f))
        val safe = transformCanvasInspection(CanvasInspectionTransform(Float.NaN, Float.NaN, Float.NaN),
            400f, 400f, 1f, zoom = Float.NaN, panX = Float.POSITIVE_INFINITY)
        assertEquals(CanvasInspectionTransform(), safe)
    }
}
