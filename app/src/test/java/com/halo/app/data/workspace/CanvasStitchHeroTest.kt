package com.halo.app.data.workspace

import org.junit.Assert.*
import org.junit.Test

class CanvasStitchHeroTest {
    @Test fun featuredImageUsesLargeLeftSlot() {
        val sources = List(4) { CanvasImageSize(800, 600) }
        val plan = CanvasStitchLayout.plan(sources,
            CanvasStitchRecipe(mode = CanvasStitchMode.HERO, maxEdge = 4096))
        assertEquals(1544, plan.width)
        assertEquals(1024, plan.height)
        assertEquals(CanvasStitchPlacement(0, 0, 1024, 1024), plan.placements.first())
        assertEquals(4, plan.placements.size)
        assertEquals(1032, plan.placements.last().x)
        assertEquals(1024, plan.placements.last().y + plan.placements.last().height)
    }

    @Test fun heroScalesDownWithoutExceedingLimits() {
        val sources = List(6) { CanvasImageSize(32000, 24000) }
        val plan = CanvasStitchLayout.plan(sources,
            CanvasStitchRecipe(mode = CanvasStitchMode.HERO, maxEdge = 1024))
        assertTrue(plan.width <= 1024)
        assertTrue(plan.height <= 1024)
        assertEquals(6, plan.placements.size)
        assertTrue(plan.placements.all { it.width > 0 && it.height > 0 })
    }

    @Test fun centerCropPreservesAspectWithoutStretching() {
        val wide = CanvasStitchCropping.centered(200, 100, 100, 100)
        assertEquals(CanvasStitchCrop(50, 0, 150, 100), wide)
        val tall = CanvasStitchCropping.centered(100, 200, 100, 100)
        assertEquals(CanvasStitchCrop(0, 50, 100, 150), tall)
    }

    @Test fun cropNeverEscapesSourceBoundaries() {
        val data = listOf(1 to 32768, 32768 to 1, 91 to 73, 1 to 1)
        data.forEach { (width, height) ->
            val crop = CanvasStitchCropping.centered(width, height, 1024, 96)
            assertTrue(crop.left >= 0 && crop.top >= 0)
            assertTrue(crop.right <= width && crop.bottom <= height)
            assertTrue(crop.right > crop.left && crop.bottom > crop.top)
        }
    }
}
