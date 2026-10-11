package com.halo.app.data.workspace

import org.junit.Assert.*
import org.junit.Test

class CanvasPdfPageSelectionTest {
    @Test fun parsesOrderedPageRanges() {
        assertEquals(listOf(1, 3, 4, 5), CanvasPdfPageSelection.parse("1,3-5", 10))
        assertEquals(listOf(5, 2, 3), CanvasPdfPageSelection.parse("5,2–3", 8))
        assertEquals(listOf(1), CanvasPdfPageSelection.parse("1", 1))
    }
    @Test fun rejectsDuplicatesAndInvalidRanges() {
        listOf("1,1", "1-4,3", "0", "9-11", "5-3", "x").forEach {
            assertNull(CanvasPdfPageSelection.parse(it, 10))
        }
    }
    @Test fun refusesExcessiveSelection() {
        assertNull(CanvasPdfPageSelection.parse("1-7", 100))
        assertNull(CanvasPdfPageSelection.parse("1,2,3,4,5,6,7", 100))
        assertNull(CanvasPdfPageSelection.parse("9999999999999999999", 100))
        assertNull(CanvasPdfPageSelection.parse("", 5))
    }
}
