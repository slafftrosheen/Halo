package com.halo.app.data.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasBatchPlannerTest {
    @Test fun duplicateAndBlankInputsHaveDistinctRejectionEntries() {
        val plan = CanvasBatchPlanner.plan(
            listOf(
                CanvasBatchSource("a", "image/png", true),
                CanvasBatchSource("a", "image/png", true),
                CanvasBatchSource("", "image/png", true),
                CanvasBatchSource("", "image/png", true),
            ),
            CanvasBatchOperation.ROTATE,
        )
        assertEquals(listOf("a"), plan.accepted.map { it.id })
        assertEquals(3, plan.rejected.size)
        assertEquals("Duplicate asset", plan.rejected["1:a"])
        assertEquals("Missing asset ID", plan.rejected["2:"])
        assertEquals("Missing asset ID", plan.rejected["3:"])
    }

    @Test fun conversionKeepsGeometryAndChangesFormat() {
        val recipe = CanvasImageRecipe(quarterTurns = 2, squareCrop = true, maxEdge = 1024)
        val plan = CanvasBatchPlanner.plan(
            listOf(CanvasBatchSource("a", "image/webp", true)),
            CanvasBatchOperation.CONVERT_JPEG,
            recipe,
        )
        assertTrue(plan.rejected.isEmpty())
        assertEquals(CanvasImageFormat.JPEG, plan.imageRecipe.format)
        assertEquals(2, plan.imageRecipe.quarterTurns)
        assertTrue(plan.imageRecipe.squareCrop)
    }

    @Test(expected = IllegalArgumentException::class)
    fun batchSizeIsBounded() {
        CanvasBatchPlanner.plan(
            List(33) { CanvasBatchSource("asset-$it", "image/png", true) },
            CanvasBatchOperation.RESIZE,
        )
    }
}
