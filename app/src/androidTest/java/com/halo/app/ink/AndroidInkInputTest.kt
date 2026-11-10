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
    private fun send(f: Fixture, action: Int, at: Long, vararg pointers: Pointer, buttons: Int = 0, flags: Int = 0,
        deviceId: Int = 0, downTime: Long = 100, source: Int = InputDevice.SOURCE_TOUCHSCREEN) {
        val properties = pointers.map { p -> MotionEvent.PointerProperties().apply { id = p.id; toolType = p.tool } }.toTypedArray()
        val coordinates = pointers.map { p -> MotionEvent.PointerCoords().apply { x = p.x; y = p.y; pressure = 0.5f; size = 0.1f } }.toTypedArray()
        val event = MotionEvent.obtain(downTime, at, action, pointers.size, properties, coordinates, 0, buttons,
            1f, 1f, deviceId, 0, source, flags)
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
        assertNull(AndroidInkInput.normalizePressure(4.1f, 0f, 4f))
    }
    @Test fun anotherDeviceCannotMoveFinishOrCancelAnActiveStroke() {
        val f = Fixture(); val p = Pointer(0, MotionEvent.TOOL_TYPE_STYLUS, 100f, 100f)
        send(f, MotionEvent.ACTION_DOWN, 100, p, deviceId = 11)
        send(f, MotionEvent.ACTION_MOVE, 110, p.copy(x = 700f), deviceId = 22)
        send(f, MotionEvent.ACTION_UP, 120, p.copy(x = 800f), deviceId = 22)
        send(f, MotionEvent.ACTION_CANCEL, 130, p, deviceId = 22)
        assertTrue(f.editor.active); assertEquals(0, f.commits)
        send(f, MotionEvent.ACTION_UP, 140, p.copy(x = 200f), deviceId = 11)
        assertEquals(listOf(100, 200), f.editor.document.strokes.single().points.map { it.x })
    }
    @Test fun removedDeviceCancelsOnlyItsUncommittedStroke() {
        val f = Fixture(); val p = Pointer(0, MotionEvent.TOOL_TYPE_STYLUS, 100f, 100f)
        send(f, MotionEvent.ACTION_DOWN, 100, p, deviceId = 11)
        f.input.deviceUnavailable(22)
        assertTrue(f.editor.active)
        f.input.deviceUnavailable(11)
        assertFalse(f.editor.active); assertEquals(0, f.commits)
        send(f, MotionEvent.ACTION_UP, 110, p, deviceId = 11)
        assertTrue(f.editor.document.strokes.isEmpty())
        send(f, MotionEvent.ACTION_DOWN, 120, p, deviceId = 22)
        send(f, MotionEvent.ACTION_UP, 130, p, deviceId = 22)
        assertEquals(1, f.commits)
    }
    @Test fun externalPenPreemptsTouchButOldTouchUpdatesCannotAffectIt() {
        val f = Fixture()
        val finger = Pointer(0, MotionEvent.TOOL_TYPE_FINGER, 10f, 10f)
        val pen = Pointer(0, MotionEvent.TOOL_TYPE_STYLUS, 300f, 400f)
        send(f, MotionEvent.ACTION_DOWN, 100, finger, deviceId = 11)
        send(f, MotionEvent.ACTION_DOWN, 110, pen, deviceId = 22, downTime = 110)
        send(f, MotionEvent.ACTION_MOVE, 120, finger.copy(x = 900f), deviceId = 11)
        send(f, MotionEvent.ACTION_CANCEL, 125, finger, deviceId = 11)
        send(f, MotionEvent.ACTION_UP, 130, finger, deviceId = 11)
        send(f, MotionEvent.ACTION_UP, 140, pen.copy(x = 400f), deviceId = 22, downTime = 110)
        assertEquals(1, f.commits)
        assertEquals(InkTool.STYLUS, f.editor.document.strokes.single().tool)
        assertEquals(listOf(300, 400), f.editor.document.strokes.single().points.map { it.x })
    }
    @Test fun oldGestureUpCannotFinishAReplacementOnTheSameDevice() {
        val f = Fixture(); val p = Pointer(0, MotionEvent.TOOL_TYPE_STYLUS, 100f, 100f)
        send(f, MotionEvent.ACTION_DOWN, 100, p, downTime = 100)
        send(f, MotionEvent.ACTION_DOWN, 200, p.copy(x = 300f), downTime = 200)
        send(f, MotionEvent.ACTION_UP, 210, p, downTime = 100)
        assertEquals(0, f.commits); assertTrue(f.editor.active)
        send(f, MotionEvent.ACTION_UP, 220, p.copy(x = 400f), downTime = 200)
        assertEquals(listOf(300, 400), f.editor.document.strokes.single().points.map { it.x })
    }
    @Test fun penHoverPreemptsTouchAndRemovalClearsPalmSuppression() {
        val f = Fixture(); val finger = Pointer(0, MotionEvent.TOOL_TYPE_FINGER, 100f, 100f)
        val pen = Pointer(0, MotionEvent.TOOL_TYPE_STYLUS, 300f, 300f)
        send(f, MotionEvent.ACTION_DOWN, 100, finger, deviceId = 11)
        send(f, MotionEvent.ACTION_HOVER_ENTER, 110, pen, deviceId = 22, downTime = 110)
        assertFalse(f.editor.active); assertEquals(0, f.commits)
        f.input.deviceUnavailable(22)
        send(f, MotionEvent.ACTION_DOWN, 120, finger, deviceId = 11, downTime = 120)
        send(f, MotionEvent.ACTION_UP, 130, finger, deviceId = 11, downTime = 120)
        assertEquals(1, f.commits)
    }
    @Test fun nonPointerSourcesAndUnknownTouchToolsCannotBecomeFingerInk() {
        val f = Fixture(); val p = Pointer(0, MotionEvent.TOOL_TYPE_FINGER, 100f, 100f)
        send(f, MotionEvent.ACTION_DOWN, 100, p, source = InputDevice.SOURCE_JOYSTICK)
        send(f, MotionEvent.ACTION_UP, 110, p, source = InputDevice.SOURCE_JOYSTICK)
        send(f, MotionEvent.ACTION_DOWN, 120, p.copy(tool = MotionEvent.TOOL_TYPE_UNKNOWN))
        send(f, MotionEvent.ACTION_UP, 130, p.copy(tool = MotionEvent.TOOL_TYPE_UNKNOWN))
        assertFalse(f.editor.active); assertEquals(0, f.commits)
    }
    @Test fun unknownToolWithMouseSourceUsesMouseFallbackWithoutPressure() {
        val f = Fixture(); val p = Pointer(0, MotionEvent.TOOL_TYPE_UNKNOWN, 100f, 100f)
        send(f, MotionEvent.ACTION_DOWN, 100, p, source = InputDevice.SOURCE_MOUSE, buttons = MotionEvent.BUTTON_PRIMARY)
        send(f, MotionEvent.ACTION_UP, 110, p, source = InputDevice.SOURCE_MOUSE)
        val stroke = f.editor.document.strokes.single()
        assertEquals(InkTool.MOUSE, stroke.tool); assertNull(stroke.points.single().pressure)
    }
    @Test fun missingPointerChangedToolOrInvalidCoordinatesCancelPendingInk() {
        val p = Pointer(0, MotionEvent.TOOL_TYPE_STYLUS, 100f, 100f)
        listOf(p.copy(id = 1), p.copy(tool = MotionEvent.TOOL_TYPE_FINGER), p.copy(x = Float.NaN)).forEach { invalid ->
            val f = Fixture()
            send(f, MotionEvent.ACTION_DOWN, 100, p)
            send(f, MotionEvent.ACTION_MOVE, 110, invalid)
            send(f, MotionEvent.ACTION_UP, 120, p)
            assertFalse(f.editor.active); assertEquals(0, f.commits)
        }
    }
    @Test fun mouseMoveWithoutPrimaryButtonCancelsPendingInk() {
        val f = Fixture(); val p = Pointer(0, MotionEvent.TOOL_TYPE_MOUSE, 100f, 100f)
        send(f, MotionEvent.ACTION_DOWN, 100, p, buttons = MotionEvent.BUTTON_PRIMARY)
        send(f, MotionEvent.ACTION_MOVE, 110, p.copy(x = 200f))
        send(f, MotionEvent.ACTION_UP, 120, p)
        assertEquals(0, f.commits)
    }
    @Test fun batchedMoveSamplesRemainInOrder() {
        val f = Fixture(); val p = Pointer(0, MotionEvent.TOOL_TYPE_FINGER, 10f, 10f)
        send(f, MotionEvent.ACTION_DOWN, 100, p)
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { x = 20f; y = 20f; pressure = 1f })
        val move = MotionEvent.obtain(100, 110, MotionEvent.ACTION_MOVE, 1, properties, coordinates, 0, 0,
            1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try {
            move.addBatch(120, 30f, 30f, 1f, 1f, 0)
            f.input.motion(move)
        } finally { move.recycle() }
        send(f, MotionEvent.ACTION_UP, 130, p.copy(x = 40f, y = 40f))
        assertEquals(listOf(10, 20, 30, 40), f.editor.document.strokes.single().points.map { it.x })
    }
}
