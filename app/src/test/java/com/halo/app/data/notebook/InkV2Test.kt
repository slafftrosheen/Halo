package com.halo.app.data.notebook

import com.halo.sdk.runtime.ink.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InkV2Test {
    private fun stroke(x: Int = 10) = InkStroke(InkTool.STYLUS, listOf(InkPoint(x, 20), InkPoint(x + 100, 120, 30, 900)))
    @Test fun metadataRoundTrips() {
        val doc = InkDocument(listOf(InkStroke(InkTool.STYLUS, listOf(
            InkPoint(0, 1000, 0, 0, 0, -3142, 32), InkPoint(1000, 0, 123, 1000, 1571, 3142, 0)))), 1400)
        assertEquals(doc, InkCodec.decode(InkCodec.encode(doc)))
    }
    @Test fun legacyPointsAndToolArePreserved() {
        val old = "sketch:v1|0,1000;500,600|1000,0"
        val doc = requireNotNull(InkCodec.decode(old))
        assertEquals(InkTool.LEGACY, doc.strokes.first().tool)
        assertEquals(listOf(InkPoint(0, 1000), InkPoint(500, 600)), doc.strokes.first().points)
        assertEquals(doc, InkCodec.decode(InkCodec.encode(doc)))
        assertEquals(CanvasSketchCodec.decode(old), CanvasSketchCodec.decode(InkCodec.encode(doc)))
    }
    @Test fun emptyAndUnreadableAreDifferent() {
        assertEquals(emptyList<InkStroke>(), InkCodec.decode("")?.strokes)
        listOf("sketch:v3|", "sketch:v2|1400|S@", "sketch:v1|1,2|bad", "sketch:v1|0,0|").forEach { assertNull(it, InkCodec.decode(it)) }
    }
    @Test fun legacyLimitsDoNotChange() {
        assertNull(InkCodec.decode("sketch:v1|" + List(257) { "0,0" }.joinToString(";")))
        assertNull(InkCodec.decode("sketch:v1|" + List(65) { "0,0" }.joinToString("|")))
    }
    @Test fun invalidMetadataAndUnknownToolsAreRejected() {
        listOf("X", "ERASER").forEach { assertNull(InkCodec.decode("sketch:v2|1400|$it@0,0,0,_,_,_,0")) }
        listOf("1001,0,0,_,_,_,0", "0,0,-1,_,_,_,0", "0,0,0,1001,_,_,0", "0,0,0,_,1572,_,0", "0,0,0,_,_,3143,0", "0,0,0,_,_,_,-1", "0,0,0,_,_,_,0,0").forEach {
            assertNull(it, InkCodec.decode("sketch:v2|1400|S@$it"))
        }
    }
    @Test fun decreasingTimesRejected() { assertNull(InkCodec.decode("sketch:v2|1400|S@0,0,2,_,_,_,0;1,1,1,_,_,_,0")) }
    @Test fun oversizeRejectedBeforeParsing() { assertNull(InkCodec.decode("sketch:v2|1400|" + "x".repeat(InkLimits.MAX_ENCODED_CHARS))) }
    @Test fun tapIsACompleteStroke() {
        val editor = InkEditor(InkDocument())
        assertTrue(editor.begin(InkTool.FINGER)); editor.sample(InkPoint(10, 20)); assertTrue(editor.finish())
        assertEquals(listOf(InkPoint(10, 20)), editor.document.strokes.single().points)
    }
    @Test fun cancelledStrokeDoesNotAlterHistory() {
        val initial = InkDocument(listOf(stroke()))
        val editor = InkEditor(initial)
        editor.begin(InkTool.STYLUS); editor.sample(InkPoint(20, 30)); editor.cancel()
        assertEquals(initial, editor.document); assertFalse(editor.canUndo)
    }
    @Test fun undoRedoAndBranching() {
        val editor = InkEditor(InkDocument())
        editor.begin(InkTool.FINGER); editor.sample(InkPoint(1, 2)); editor.finish()
        val first = editor.document
        assertTrue(editor.undo()); assertTrue(editor.redo()); assertEquals(first, editor.document)
        editor.undo(); editor.begin(InkTool.MOUSE); editor.sample(InkPoint(3, 4)); editor.finish()
        assertFalse(editor.canRedo)
    }
    @Test fun eraseUsesSegmentsNotOnlyRecordedPoints() {
        val original = InkDocument(listOf(InkStroke(InkTool.FINGER, listOf(InkPoint(0, 500), InkPoint(1000, 500)))))
        val editor = InkEditor(original)
        editor.begin(InkTool.STYLUS, erasing = true)
        editor.sample(InkPoint(500, 0), 10f, 10f); editor.sample(InkPoint(500, 1000), 10f, 10f)
        assertTrue(editor.finish()); assertTrue(editor.document.strokes.isEmpty())
        editor.undo(); assertEquals(original, editor.document)
    }
    @Test fun cancelledEraserRestoresAllRemovedStrokes() {
        val original = InkDocument(listOf(stroke()))
        val editor = InkEditor(original)
        editor.begin(InkTool.STYLUS, erasing = true); editor.sample(InkPoint(10, 20)); editor.cancel()
        assertEquals(original, editor.document); assertFalse(editor.canUndo)
    }
    @Test fun clearIsUndoableAndNoOpIsNotAnUndoEntry() {
        val editor = InkEditor(InkDocument(listOf(stroke())))
        assertTrue(editor.clear()); assertFalse(editor.clear()); assertTrue(editor.undo()); assertFalse(editor.canUndo)
    }
    @Test fun pointsAreBoundedAndLimitIsReported() {
        val editor = InkEditor(InkDocument()); editor.begin(InkTool.STYLUS)
        repeat(InkLimits.MAX_POINTS_PER_STROKE + 10) { editor.sample(InkPoint(it % 1000, 20, it)) }
        assertTrue(editor.limitReached); editor.finish()
        assertEquals(InkLimits.MAX_POINTS_PER_STROKE, editor.document.strokes.single().points.size)
    }
    @Test fun legacyEncodingApiRemainsLegacy() {
        assertTrue(CanvasSketchCodec.encode(listOf(listOf(CanvasSketchPoint(1, 2)))).startsWith("sketch:v1|"))
    }
    @Test fun fileMarkupRetainsExactV2Payload() {
        val encoded = InkCodec.encode(InkDocument(listOf(stroke())))
        val saved = CanvasFileMarkupCodec.merge("PDF", mapOf(1 to encoded))
        assertEquals(encoded, CanvasFileMarkupCodec.decode(saved).pages[1])
    }
    @Test fun draftRetainsV2AndDoesNotModifyOriginal() {
        val original = "sketch:v1|1,2"
        val draft = CanvasDrawingDraft("node", "sketch", original, InkCodec.encode(InkDocument(listOf(stroke()))))
        assertEquals(draft, CanvasDrawingDraft.decode(draft.encode()))
    }
    @Test fun fullLegacyDrawingKeepsCompactEncoding() {
        val legacy = "sketch:v1|" + List(64) { List(256) { "1000,1000" }.joinToString(";") }.joinToString("|")
        val document = requireNotNull(InkCodec.decode(legacy))
        val encoded = InkCodec.encode(document)
        assertTrue(encoded.length < 200_000)
        assertEquals(document, InkCodec.decode(encoded))
    }
    @Test fun unknownSensorDoesNotBecomeZeroPressure() {
        val document = InkDocument(listOf(InkStroke(InkTool.STYLUS, listOf(InkPoint(10, 20)))))
        assertNull(InkCodec.decode(InkCodec.encode(document))?.strokes?.single()?.points?.single()?.pressure)
    }
    @Test(expected = IllegalArgumentException::class)
    fun legacyToolCannotSilentlyDropMetadata() {
        InkCodec.encode(InkDocument(listOf(InkStroke(InkTool.LEGACY, listOf(InkPoint(0, 0, pressure = 10))))))
    }
    @Test fun historyDoesNotRetainCallerMutableLists() {
        val mutable = mutableListOf(InkPoint(1, 2))
        val editor = InkEditor(InkDocument(listOf(InkStroke(InkTool.FINGER, mutable))))
        mutable.clear()
        assertEquals(1, editor.document.strokes.single().points.size)
    }
    @Test fun historyCountIsBounded() {
        val editor = InkEditor(InkDocument())
        repeat(24) { editor.begin(InkTool.FINGER); editor.sample(InkPoint(it, it)); editor.finish() }
        var undos = 0
        while (editor.undo()) undos++
        assertEquals(InkLimits.MAX_HISTORY, undos)
    }
    @Test fun eraserDoesNotDeleteDistantParallelSegment() {
        val stroke = InkStroke(InkTool.FINGER, listOf(InkPoint(0, 500), InkPoint(1000, 500)))
        assertFalse(InkGeometry.intersects(stroke, InkPoint(0, 100), InkPoint(1000, 100), 10f, 10f))
    }
    @Test fun oversizedV2StrokeIsUnreadable() {
        val point = "0,0,0,_,_,_,0"
        assertNull(InkCodec.decode("sketch:v2|1400|S@" + List(InkLimits.MAX_POINTS_PER_STROKE + 1) { point }.joinToString(";")))
    }

    @Test fun firstEditBindsLegacyAspectAndUndoRestoresOriginal() {
        val legacy = requireNotNull(InkCodec.decode("sketch:v1|10,20"))
        val editor = InkEditor(legacy, editAspectMilli = 1400)
        assertEquals(0, editor.document.aspectMilli)
        editor.begin(InkTool.FINGER); editor.sample(InkPoint(100, 200)); editor.finish()
        assertEquals(1400, editor.document.aspectMilli)
        assertEquals(legacy.strokes.first(), editor.document.strokes.first())
        editor.undo(); assertEquals(legacy, editor.document)
    }

}
