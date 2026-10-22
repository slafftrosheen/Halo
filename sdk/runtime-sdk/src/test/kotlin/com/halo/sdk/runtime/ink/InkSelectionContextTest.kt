package com.halo.sdk.runtime.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InkSelectionContextTest {
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
}
