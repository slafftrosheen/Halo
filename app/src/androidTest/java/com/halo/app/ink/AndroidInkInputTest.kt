package com.halo.app.ink

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.halo.host.android.ink.AndroidInkInput
import com.halo.sdk.runtime.ink.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic platform events exercise the adapter; they do NOT qualify a physical pen. */
@RunWith(AndroidJUnit4::class)
class AndroidInkInputTest {
    private data class Pointer(val id: Int, val tool: Int, val x: Float, val y: Float)
    private class Fixture(initial: InkDocument = InkDocument()) {
        val editor = InkEditor(initial)
        var commits = 0
        var hovers = 0
        val input = AndroidInkInput(editor, { x, y -> x to y }, { 15f to 15f }, { false }, {}, { commits++ }, { hovers++ })
    }
    private fun send(f: Fixture, action: Int, at: Long, vararg pointers: Pointer, buttons: Int = 0, flags: Int = 0) {
        val properties = pointers.map { p -> MotionEvent.PointerProperties().apply { id = p.id; toolType = p.tool } }.toTypedArray()
        val coordinates = pointers.map { p -> MotionEvent.PointerCoords().apply { x = p.x; y = p.y; pressure = 0.5f; size = 0.1f } }.toTypedArray()
        val event = MotionEvent.obtain(100, at, action, pointers.size, properties, coordinates, 0, buttons,
            1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, flags)
        try { f.input.motion(event) } finally { event.recycle() }
    }
    @Test fun fingerTapHasNoTouchSlopRequirement() {
        val f = Fixture(); val p = Pointer(7, MotionEvent.TOOL_TYPE_FINGER, 10f, 20f)
        send(f, MotionEvent.ACTION_DOWN, 100, p); send(f, MotionEvent.ACTION_UP, 120, p)
        assertEquals(1, f.commits); assertEquals(InkTool.FINGER, f.editor.document.strokes.single().tool)
    }
    @Test fun cancellationNeverCommitsPalmInk() {
        val f = Fixture(); val p = Pointer(7, MotionEvent.TOOL_TYPE_FINGER, 10f, 20f)
        send(f, MotionEvent.ACTION_DOWN, 100, p); send(f, MotionEvent.ACTION_MOVE, 110, p.copy(x = 50f))
        send(f, MotionEvent.ACTION_CANCEL, 120, p)
        assertEquals(0, f.commits); assertTrue(f.editor.document.strokes.isEmpty())
    }
    @Test @SdkSuppress(minSdkVersion = 33)
    fun flaggedPointerUpRollsBackContact() {
        val f = Fixture(); val p = Pointer(7, MotionEvent.TOOL_TYPE_FINGER, 10f, 20f)
        send(f, MotionEvent.ACTION_DOWN, 100, p)
        send(f, MotionEvent.ACTION_UP, 120, p, flags = MotionEvent.FLAG_CANCELED)
        assertEquals(0, f.commits); assertFalse(f.editor.active)
    }
    @Test fun penPreemptsFingerAndTracksIdAcrossReorderedIndexes() {
        val f = Fixture(); val finger = Pointer(7, MotionEvent.TOOL_TYPE_FINGER, 10f, 20f)
        val pen = Pointer(21, MotionEvent.TOOL_TYPE_STYLUS, 300f, 400f)
        send(f, MotionEvent.ACTION_DOWN, 100, finger)
        send(f, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 110, finger, pen)
        send(f, MotionEvent.ACTION_MOVE, 120, pen.copy(x = 350f), finger)
        send(f, MotionEvent.ACTION_POINTER_UP, 130, pen.copy(x = 400f), finger)
        assertEquals(1, f.commits)
        val stroke = f.editor.document.strokes.single()
        assertEquals(InkTool.STYLUS, stroke.tool); assertEquals(300, stroke.points.first().x)
        assertEquals(400, stroke.points.last().x)
    }
    @Test fun physicalEraserRemovesWholeStrokeAndUndoRestoresIt() {
        val initial = InkDocument(listOf(InkStroke(InkTool.FINGER, listOf(InkPoint(0, 500), InkPoint(1000, 500)))))
        val f = Fixture(initial); val eraser = Pointer(3, MotionEvent.TOOL_TYPE_ERASER, 500f, 500f)
        send(f, MotionEvent.ACTION_DOWN, 100, eraser); send(f, MotionEvent.ACTION_UP, 120, eraser)
        assertTrue(f.editor.document.strokes.isEmpty()); f.editor.undo(); assertEquals(initial, f.editor.document)
    }
    @Test fun barrelButtonAtContactStartSelectsEraser() {
        val f = Fixture(InkDocument(listOf(InkStroke(InkTool.FINGER, listOf(InkPoint(100, 100))))))
        val p = Pointer(3, MotionEvent.TOOL_TYPE_STYLUS, 100f, 100f)
        send(f, MotionEvent.ACTION_DOWN, 100, p, buttons = MotionEvent.BUTTON_STYLUS_PRIMARY)
        send(f, MotionEvent.ACTION_UP, 120, p)
        assertTrue(f.editor.document.strokes.isEmpty()); assertEquals(1, f.commits)
    }
    @Test fun mouseSecondaryButtonCannotDraw() {
        val f = Fixture(); val p = Pointer(2, MotionEvent.TOOL_TYPE_MOUSE, 100f, 100f)
        send(f, MotionEvent.ACTION_DOWN, 100, p, buttons = MotionEvent.BUTTON_SECONDARY)
        send(f, MotionEvent.ACTION_UP, 120, p)
        assertTrue(f.editor.document.strokes.isEmpty()); assertEquals(0, f.commits)
    }
    @Test fun hoverDoesNotBecomeAStroke() {
        val f = Fixture(); val p = Pointer(2, MotionEvent.TOOL_TYPE_STYLUS, 100f, 100f)
        send(f, MotionEvent.ACTION_HOVER_ENTER, 100, p); send(f, MotionEvent.ACTION_HOVER_MOVE, 120, p)
        assertTrue(f.editor.document.strokes.isEmpty()); assertTrue(f.hovers > 0)
    }
    @Test fun pressureUsesAdvertisedRangeAndRejectsInvalidValues() {
        assertEquals(500, AndroidInkInput.normalizePressure(2f, 0f, 4f))
        assertEquals(0, AndroidInkInput.normalizePressure(0f, 0f, 1f))
        assertNull(AndroidInkInput.normalizePressure(Float.NaN, 0f, 1f))
        assertNull(AndroidInkInput.normalizePressure(0.5f, 1f, 1f))
    }
    @Test fun batchedMoveSamplesRemainInOrder() {
        val f = Fixture(); val p = Pointer(0, MotionEvent.TOOL_TYPE_FINGER, 10f, 10f)
        send(f, MotionEvent.ACTION_DOWN, 100, p)
        val move = MotionEvent.obtain(100, 110, MotionEvent.ACTION_MOVE, 20f, 20f, 0)
        try {
            move.addBatch(120, 30f, 30f, 1f, 1f, 0)
            f.input.motion(move)
        } finally { move.recycle() }
        send(f, MotionEvent.ACTION_UP, 130, p.copy(x = 40f, y = 40f))
        assertEquals(listOf(10, 20, 30, 40), f.editor.document.strokes.single().points.map { it.x })
    }
}
