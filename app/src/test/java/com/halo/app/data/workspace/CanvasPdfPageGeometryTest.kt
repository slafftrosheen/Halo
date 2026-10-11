package com.halo.app.data.workspace

import org.junit.Assert.*
import org.junit.Test

class CanvasPdfPageGeometryTest {
    @Test fun normalLetterSizeRendersAtHighResolution() {
        assertEquals(1582 to 2048, CanvasPdfPageGeometry.dimensions(612, 792))
    }
    @Test fun portraitPreviewIsBoundedAndAspectSafe() {
        val (width, height) = CanvasPdfPageGeometry.dimensions(612, 792, 640)
        assertEquals(495, width)
        assertEquals(640, height)
    }
    @Test fun veryLongPageNeverExceedsBudget() {
        val (width, height) = CanvasPdfPageGeometry.dimensions(500, 30000)
        assertTrue(width in 1..2048 && height in 1..2048)
        assertTrue(width.toLong() * height <= 12_000_000L)
    }
    @Test fun invalidSizesAndOutputCapsFail() {
        assertThrows(IllegalArgumentException::class.java) { CanvasPdfPageGeometry.dimensions(0, 100) }
        assertThrows(IllegalArgumentException::class.java) { CanvasPdfPageGeometry.dimensions(100, 100, 8192) }
    }
}
