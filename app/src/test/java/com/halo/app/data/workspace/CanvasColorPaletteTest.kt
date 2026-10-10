package com.halo.app.data.workspace

import org.junit.Assert.*
import org.junit.Test

class CanvasColorPaletteTest {
    @Test fun dominantOpaqueColorAppearsFirst() {
        val samples = IntArray(80) { if (it < 60) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }
        assertEquals(listOf("#FF0000", "#0000FF"), CanvasColorPalette.extract(samples))
    }
    @Test fun transparencyIgnored() {
        val samples = IntArray(60) { if (it < 50) 0x00000000 else 0xFF00FF00.toInt() }
        assertEquals(listOf("#00FF00"), CanvasColorPalette.extract(samples))
    }
    @Test fun nearlyIdenticalColorsCombine() {
        assertEquals(1, CanvasColorPalette.extract(intArrayOf(0xFF181818.toInt(), 0xFF191919.toInt())).size)
    }
    @Test fun limitEnforcedAndAllTransparentReturnsEmpty() {
        assertTrue(CanvasColorPalette.extract(intArrayOf(0x00000000)).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { CanvasColorPalette.extract(intArrayOf(0xFFFFFFFF.toInt()), 0) }
    }
}
