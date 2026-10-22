package com.halo.sdk.runtime.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InkCodecStableStrokeIdTest {
    @Test fun v3RoundTripPreservesStrokeIdentityAcrossEdits() {
        val original = InkStroke(InkTool.STYLUS, listOf(InkPoint(10, 20)), strokeId = "stroke-a")
        val encoded = InkCodec.encode(InkDocument(listOf(original)))
        val reopened = requireNotNull(InkCodec.decode(encoded)).strokes.single()
        val edited = reopened.copy(points = reopened.points + InkPoint(20, 30, 1))

        assertTrue(encoded.startsWith("sketch:v3|"))
        assertEquals("stroke-a", reopened.strokeId)
        assertEquals(reopened.strokeId, edited.strokeId)
        assertNotEquals("stroke-b", edited.strokeId)
    }

    @Test fun legacyFormatsReceiveRepeatableIdsWhenOpened() {
        val v1 = requireNotNull(InkCodec.decode("sketch:v1|10,20|30,40"))
        val v2 = requireNotNull(InkCodec.decode("sketch:v2|1400|S@10,20,0,_,_,_,0"))

        assertEquals(v1.strokes.map { it.strokeId }, requireNotNull(InkCodec.decode("sketch:v1|10,20|30,40")).strokes.map { it.strokeId })
        assertEquals(v2.strokes.single().strokeId, requireNotNull(InkCodec.decode(InkCodec.encode(v2))).strokes.single().strokeId)
        assertNotNull(v2.strokes.single().strokeId)
    }

    @Test fun previewKeepsTheSameIdentityUntilTheStrokeIsCommitted() {
        val editor = InkEditor(InkDocument())
        editor.begin(InkTool.STYLUS)
        editor.sample(InkPoint(10, 20))
        assertEquals(editor.preview().strokes.single().strokeId, editor.preview().strokes.single().strokeId)
    }

    @Test fun regionSelectionIncludesCrossingSegmentsButExcludesDistantStrokes() {
        val crossing = InkStroke(InkTool.STYLUS, listOf(InkPoint(0, 500), InkPoint(1000, 500)))
        val distant = InkStroke(InkTool.FINGER, listOf(InkPoint(0, 100), InkPoint(1000, 100)))
        val bounds = InkSelectionBounds(400, 400, 600, 600)

        assertEquals(true, InkGeometry.intersects(crossing, bounds))
        assertEquals(false, InkGeometry.intersects(distant, bounds))
    }
}
