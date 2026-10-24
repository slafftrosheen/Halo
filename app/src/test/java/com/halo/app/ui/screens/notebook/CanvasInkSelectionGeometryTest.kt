package com.halo.app.ui.screens.notebook

import androidx.compose.ui.geometry.Rect
import com.halo.sdk.runtime.ink.InkSelectionBounds
import org.junit.Assert.*
import org.junit.Test

class CanvasInkSelectionGeometryTest {
    @Test fun sourceAspectAndRegionDimensionsBothDetermineTheVisibleShape() {
        val bounds = InkSelectionBounds(0, 0, 1000, 500)
        assertEquals(4f, inkSelectionAspect(2000, bounds), 0.0001f)
        val frame = inkSelectionFrame(768f, 768f, 2000, bounds)
        assertEquals(Rect(0f, 288f, 768f, 480f), frame)
    }

    @Test fun legacyFallbackStillAccountsForTheSelectedBounds() {
        val bounds = InkSelectionBounds(250, 0, 750, 1000)
        assertEquals(0.7f, inkSelectionAspect(0, bounds), 0.0001f)
        val frame = inkSelectionFrame(768f, 768f, 0, bounds)
        assertEquals(0.7f, frame.width / frame.height, 0.0001f)
        assertEquals(768f, frame.height, 0.0001f)
    }

    @Test fun minimumWidthBitmapDoesNotWidenAnExtremePortraitRegion() {
        val bounds = InkSelectionBounds(499, 0, 500, 1000)
        val frame = inkSelectionFrame(96f, 768f, 125, bounds)
        assertEquals(0.000125f, frame.width / frame.height, 0.0000002f)
        assertTrue(frame.left > 47f && frame.right < 49f)
        assertEquals(768f, frame.height, 0.001f)
    }

    @Test fun minimumHeightBitmapDoesNotHeightenAnExtremeLandscapeRegion() {
        val bounds = InkSelectionBounds(0, 499, 1000, 500)
        val frame = inkSelectionFrame(768f, 96f, 8000, bounds)
        assertEquals(8000f, frame.width / frame.height, 8f)
        assertTrue(frame.top > 47f && frame.bottom < 49f)
        assertEquals(768f, frame.width, 0.001f)
    }

    @Test fun originalDocumentCoordinatesMapToTheSameCropInEveryViewport() {
        val bounds = InkSelectionBounds(100, 250, 600, 900)
        for ((width, height) in listOf(360f to 180f, 96f to 768f, 768f to 96f)) {
            val frame = inkSelectionFrame(width, height, 2300, bounds)
            val source = inkSelectionSourceRect(frame, bounds)
            fun x(value: Int) = source.left + value * source.width / 1000f
            fun y(value: Int) = source.top + value * source.height / 1000f
            assertEquals(frame.left, x(bounds.left), 0.001f)
            assertEquals(frame.right, x(bounds.right), 0.001f)
            assertEquals(frame.top, y(bounds.top), 0.001f)
            assertEquals(frame.bottom, y(bounds.bottom), 0.001f)
            assertEquals(2.3f, source.width / source.height, 0.001f)
            assertTrue(frame.left >= 0f && frame.top >= 0f && frame.right <= width && frame.bottom <= height)
        }
    }

    @Test fun zeroSizedCompositionHasNoInvalidOrInfiniteProjection() {
        val bounds = InkSelectionBounds(0, 0, 1000, 1000)
        assertEquals(Rect.Zero, inkSelectionFrame(0f, 96f, 1400, bounds))
        assertEquals(Rect.Zero, inkSelectionFrame(96f, 0f, 1400, bounds))
        assertEquals(Rect.Zero, inkSelectionSourceRect(Rect.Zero, bounds))
    }
}
