package com.halo.app.data.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasSketchCodecTest {
    @Test fun roundTripMultipleStrokes() {
        val strokes = listOf(
            listOf(CanvasSketchPoint(0, 1000), CanvasSketchPoint(400, 555), CanvasSketchPoint(1000, 0)),
            listOf(CanvasSketchPoint(100, 250)),
        )
        assertEquals(strokes, CanvasSketchCodec.decode(CanvasSketchCodec.encode(strokes)))
    }

    @Test fun clampsOutOfBoundsInputOnEncoding() {
        val encoded = CanvasSketchCodec.encode(listOf(listOf(CanvasSketchPoint(-1, 5000))))
        assertEquals(listOf(listOf(CanvasSketchPoint(0, 1000))), CanvasSketchCodec.decode(encoded))
    }

    @Test fun rejectsCorruptionAndOversizedInput() {
        assertTrue(CanvasSketchCodec.decode("v2|0,0").isEmpty())
        assertTrue(CanvasSketchCodec.decode("sketch:v1|3,what").isEmpty())
        assertTrue(CanvasSketchCodec.decode("sketch:v1|" + "0,0;".repeat(257)).isEmpty())
        assertTrue(CanvasSketchCodec.decode("sketch:v1|" + "a".repeat(200_001)).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedDrawingWhenSaving() {
        CanvasSketchCodec.encode((0..CanvasSketchCodec.MAX_STROKES).map { listOf(CanvasSketchPoint(1, 1)) })
    }
}
