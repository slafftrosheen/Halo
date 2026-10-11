package com.halo.app.data.workspace

import org.junit.Assert.*
import org.junit.Test

class CanvasStitchLayoutTest {
    private val two = listOf(CanvasImageSize(100, 200), CanvasImageSize(200, 100))
    @Test fun horizontalPlacementRetainsSelectionOrder() {
        val p = CanvasStitchLayout.plan(two, CanvasStitchRecipe(mode = CanvasStitchMode.HORIZONTAL, gap = 8))
        assertEquals(308, p.width)
        assertEquals(200, p.height)
        assertEquals(CanvasStitchPlacement(0, 0, 100, 200), p.placements[0])
        assertEquals(CanvasStitchPlacement(108, 50, 200, 100), p.placements[1])
    }
    @Test fun verticalPlacementCentresImages() {
        val p = CanvasStitchLayout.plan(two, CanvasStitchRecipe(mode = CanvasStitchMode.VERTICAL, gap = 8))
        assertEquals(200, p.width)
        assertEquals(308, p.height)
        assertEquals(CanvasStitchPlacement(50, 0, 100, 200), p.placements[0])
        assertEquals(CanvasStitchPlacement(0, 208, 200, 100), p.placements[1])
    }
    @Test fun sixImagesFitBoundedThreeColumnGrid() {
        val six = List(6) { CanvasImageSize(6000, 3000) }
        val p = CanvasStitchLayout.plan(six, CanvasStitchRecipe(maxEdge = 2048, gap = 24))
        assertEquals(6, p.placements.size)
        assertTrue(p.width <= 2048 && p.height <= 2048)
        assertTrue(p.placements[3].y > p.placements[0].y)
        assertTrue(p.width.toLong() * p.height <= 12_000_000)
    }
    @Test fun previewAndExportShareRelativePlacement() {
        val recipe = CanvasStitchRecipe(mode = CanvasStitchMode.GRID, maxEdge = 4096)
        val size = List(4) { CanvasImageSize(1600, 1200) }
        val export = CanvasStitchLayout.plan(size, recipe)
        val preview = CanvasStitchLayout.plan(size, recipe, 640)
        assertTrue(preview.width <= 640 && preview.height <= 640)
        assertTrue(export.width > preview.width)
    }
    @Test fun invalidRequestsFailInsteadOfUnboundedAllocation() {
        val recipe = CanvasStitchRecipe()
        assertThrows(IllegalArgumentException::class.java) { CanvasStitchLayout.plan(listOf(two[0]), recipe) }
        assertThrows(IllegalArgumentException::class.java) { CanvasStitchLayout.plan(List(7) { two[0] }, recipe) }
        assertThrows(IllegalArgumentException::class.java) { CanvasStitchRecipe(maxEdge = 9999) }
        assertThrows(IllegalArgumentException::class.java) { CanvasStitchRecipe(gap = -1) }
    }
}
