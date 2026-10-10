package com.halo.app.data.workspace

import org.junit.Assert.*
import org.junit.Test

class CanvasImageRecipeTest {
    @Test fun rotationSwapsDimensionsBeforeResize() {
        assertEquals(1024 to 512,
            CanvasImageRecipe(quarterTurns = 1, maxEdge = 1024).outputDimensions(2000, 4000))
    }
    @Test fun squareCropNeverUpscales() {
        assertEquals(900 to 900,
            CanvasImageRecipe(squareCrop = true, maxEdge = 4096).outputDimensions(3200, 900))
    }
    @Test fun aspectRatioPreserved() {
        assertEquals(2048 to 1365, CanvasImageRecipe().outputDimensions(3000, 2000))
    }
    @Test fun previewBoundedWithoutChangingExport() {
        val recipe = CanvasImageRecipe(maxEdge = 4096)
        assertEquals(600 to 400, recipe.outputDimensions(3000, 2000, previewLimit = 600))
        assertEquals(3000 to 2000, recipe.outputDimensions(3000, 2000))
    }
    @Test fun invalidInputsRejected() {
        assertThrows(IllegalArgumentException::class.java) { CanvasImageRecipe(maxEdge = 500) }
        assertThrows(IllegalArgumentException::class.java) { CanvasImageRecipe(quarterTurns = 4) }
        assertThrows(IllegalArgumentException::class.java) { CanvasImageRecipe().outputDimensions(0, 100) }
    }
    @Test fun formatsExplicit() {
        assertEquals("image/jpeg", CanvasImageFormat.JPEG.mimeType)
        assertEquals("png", CanvasImageFormat.PNG.extension)
    }
}
