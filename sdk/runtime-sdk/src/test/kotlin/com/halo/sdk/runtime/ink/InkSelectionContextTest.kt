package com.halo.sdk.runtime.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class InkSelectionContextTest {
    @Test fun selectionCapturesExactIntersectingStrokeIdsAndResolvesOnlyOnItsSourceRevision() {
        val selected = InkStroke(InkTool.STYLUS,
            listOf(InkPoint(100, 500), InkPoint(700, 500)), "stroke-selected")
        val distant = InkStroke(InkTool.FINGER,
            listOf(InkPoint(100, 100), InkPoint(700, 100)), "stroke-distant")
        val document = InkDocument(listOf(selected, distant))
        val bounds = InkSelectionBounds(400, 400, 600, 600)
        val context = requireNotNull(InkSelections.create(document, "canvas-17", "page-2", "rev-1",
            bounds, InkSelectionOrigin.BOX, 1234))

        assertEquals(listOf("stroke-selected"), context.selectedStrokeIds)
        assertEquals(listOf(selected), InkSelections.resolve(document, context,
            "canvas-17", "page-2", "rev-1"))
        assertNull(InkSelections.resolve(document, context, "canvas-17", "page-2", "rev-2"))
        assertNull(InkSelections.resolve(document, context, "canvas-18", "page-2", "rev-1"))
        assertNull(InkSelections.resolve(document, context, "canvas-17", "page-3", "rev-1"))
    }

    @Test fun emptyRegionDoesNotBecomeAnActionableSelectionAndDeletedStrokeIsRejected() {
        val stroke = InkStroke(InkTool.STYLUS,
            listOf(InkPoint(100, 100), InkPoint(200, 200)), "stroke-a")
        val document = InkDocument(listOf(stroke))
        val bounds = InkSelectionBounds(700, 700, 900, 900)

        assertNull(InkSelections.create(document, "canvas-17", null, "rev-1", bounds,
            InkSelectionOrigin.ACCESSIBILITY, 1234))

        val selected = requireNotNull(InkSelections.create(document, "canvas-17", null, "rev-1",
            InkSelectionBounds(100, 100, 300, 300), InkSelectionOrigin.BOX, 1234))
        assertNull(InkSelections.resolve(InkDocument(), selected, "canvas-17", null, "rev-1"))
    }

    @Test fun stableSelectionRetainsDocumentRegionAndProvenance() {
        val context = InkSelectionContext(
            workspaceObjectId = "canvas-17",
            pageId = "page-2",
            bounds = InkSelectionBounds(100, 200, 800, 900),
            selectedStrokeIds = listOf("stroke-a", "stroke-b"),
            sourceRevision = "revision-12",
            origin = InkSelectionOrigin.BOX,
            createdAtMs = 1000,
            updatedAtMs = 1000,
        )

        assertEquals("canvas-17", context.workspaceObjectId)
        assertEquals("page-2", context.pageId)
        assertEquals(2, context.selectedStrokeIds.size)
        assertEquals(InkSelectionOrigin.BOX, context.origin)
    }

    @Test fun invalidOrAmbiguousSelectionReferencesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            InkSelectionContext("", null, InkSelectionBounds(0, 0, 10, 10), listOf("s1"), "r1",
                InkSelectionOrigin.LASSO, 1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InkSelectionContext("canvas-17", null, InkSelectionBounds(10, 0, 10, 10), listOf("s1"), "r1",
                InkSelectionOrigin.LASSO, 1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InkSelectionContext("canvas-17", null, InkSelectionBounds(0, 0, 10, 10), listOf("s1", "s1"), "r1",
                InkSelectionOrigin.LASSO, 1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InkSelectionContext("canvas-17", null, InkSelectionBounds(0, 0, 10, 10), listOf("s1"), "r1",
                InkSelectionOrigin.LASSO, 5, 4)
        }
    }

    @Test fun selectionStrokeReferencesCannotExceedTheSourceDocumentStrokeQuota() {
        val strokeIds = (0..InkLimits.MAX_STROKES).map { "stroke-$it" }
        assertThrows(IllegalArgumentException::class.java) {
            InkSelectionContext("canvas-17", null, InkSelectionBounds(0, 0, 10, 10), strokeIds, "r1",
                InkSelectionOrigin.BOX, 1, 1)
        }
    }
}
