package com.halo.host.android.ink

import android.os.Build
import android.view.MotionEvent
import com.halo.sdk.runtime.ink.*
import kotlin.math.roundToInt

/** MotionEvent lives only at the Android boundary. Never retains a recycled event. */
class AndroidInkInput(
    private val editor: InkEditor,
    private val position: (Float, Float) -> Pair<Float, Float>?,
    private val eraserRadius: () -> Pair<Float, Float>,
    private val eraseSelected: () -> Boolean,
    private val onPreview: () -> Unit,
    private val onCommit: () -> Unit,
    private val onHover: (Pair<Float, Float>?) -> Unit = {},
) {
    private var pointerId = -1
    private var startedAt = 0L
    private var penNearbyUntil = 0L
    private var isPen = false

    /** Called on layout/mode/lifecycle changes. Incomplete gestures are not durable drafts. */
    fun abort() { editor.cancel(); pointerId = -1; isPen = false }

    fun motion(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                val i = event.actionIndex
                if (i in 0 until event.pointerCount && pen(event.getToolType(i))) {
                    penNearbyUntil = event.eventTime + 700
                    if (pointerId != -1 && !isPen) abort()
                    onHover(position(event.getX(i), event.getY(i)))
                    onPreview()
                }
                return true
            }
            MotionEvent.ACTION_HOVER_EXIT -> { penNearbyUntil = 0; onHover(null); return true }
            MotionEvent.ACTION_CANCEL -> { abort(); onHover(null); onPreview(); return true }
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                if (index !in 0 until event.pointerCount) return false
                val type = event.getToolType(index)
                val incomingPen = pen(type)
                if (event.actionMasked == MotionEvent.ACTION_DOWN) abort()
                if (pointerId != -1) {
                    if (incomingPen && !isPen) abort() // A pen wins over an uncommitted palm/finger.
                    else {
                        if (!isPen && !incomingPen) { abort(); onPreview() }
                        return true // Never connect two pointer IDs into one stroke.
                    }
                }
                if (!incomingPen && type != MotionEvent.TOOL_TYPE_MOUSE && event.eventTime < penNearbyUntil) return true
                if (type == MotionEvent.TOOL_TYPE_MOUSE && event.buttonState and MotionEvent.BUTTON_PRIMARY == 0) return false
                if (position(event.getX(index), event.getY(index)) == null) return false
                val tool = when (type) {
                    MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> InkTool.STYLUS
                    MotionEvent.TOOL_TYPE_MOUSE -> InkTool.MOUSE
                    else -> InkTool.FINGER
                }
                val erasing = eraseSelected() || type == MotionEvent.TOOL_TYPE_ERASER ||
                    (incomingPen && event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0)
                if (editor.begin(tool, erasing)) {
                    pointerId = event.getPointerId(index); startedAt = event.eventTime; isPen = incomingPen
                    if (isPen) penNearbyUntil = event.eventTime + 700
                    sample(event, index)
                }
                onHover(null); onPreview(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) return true
                if (isPen) penNearbyUntil = event.eventTime + 700
                // Historical samples precede the current sample; timestamps keep that order.
                val start = (event.historySize - InkLimits.MAX_POINTS_PER_STROKE).coerceAtLeast(0)
                for (history in start until event.historySize) sample(event, index, history)
                sample(event, index); onPreview(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                if (index !in 0 until event.pointerCount || event.getPointerId(index) != pointerId) return true
                val cancelled = Build.VERSION.SDK_INT >= 33 && event.flags and MotionEvent.FLAG_CANCELED != 0
                if (cancelled) abort() else {
                    sample(event, index)
                    val changed = editor.finish()
                    pointerId = -1; isPen = false
                    if (changed) onCommit()
                }
                onPreview(); return true
            }
        }
        return false
    }

    private fun sample(event: MotionEvent, index: Int, history: Int = -1) {
        val at = if (history < 0) event.eventTime else event.getHistoricalEventTime(history)
        if (at < startedAt) return
        val x = if (history < 0) event.getX(index) else event.getHistoricalX(index, history)
        val y = if (history < 0) event.getY(index) else event.getHistoricalY(index, history)
        val mapped = position(x, y) ?: return
        if (!mapped.first.isFinite() || !mapped.second.isFinite()) return
        fun axis(axis: Int): Float = if (history < 0) event.getAxisValue(axis, index)
            else event.getHistoricalAxisValue(axis, index, history)
        val device = event.device
        val pressureRange = device?.getMotionRange(MotionEvent.AXIS_PRESSURE, event.source)
        val pressure = if (isPen && pressureRange != null)
            normalizePressure(axis(MotionEvent.AXIS_PRESSURE), pressureRange.min, pressureRange.max) else null
        val tilt = if (isPen && device?.getMotionRange(MotionEvent.AXIS_TILT, event.source) != null)
            milli(axis(MotionEvent.AXIS_TILT), 0, 1571) else null
        val orientation = if (isPen && device?.getMotionRange(MotionEvent.AXIS_ORIENTATION, event.source) != null)
            milli(axis(MotionEvent.AXIS_ORIENTATION), -3142, 3142) else null
        val radius = eraserRadius()
        editor.sample(InkPoint(mapped.first.roundToInt().coerceIn(0, 1000), mapped.second.roundToInt().coerceIn(0, 1000),
            (at - startedAt).coerceIn(0, InkLimits.MAX_CONTACT_MS.toLong()).toInt(), pressure, tilt, orientation,
            event.buttonState and 0xffff), radius.first, radius.second)
    }

    private fun pen(type: Int) = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
    private fun milli(value: Float, min: Int, max: Int): Int? =
        if (value.isFinite()) (value * 1000).roundToInt().coerceIn(min, max) else null

    companion object {
        /** Missing/invalid axes stay unknown; zero pressure is a valid measured value. */
        fun normalizePressure(raw: Float, min: Float, max: Float): Int? =
            if (!raw.isFinite() || !min.isFinite() || !max.isFinite() || !(max - min).isFinite() || max <= min) null
            else (((raw - min) / (max - min)).coerceIn(0f, 1f) * 1000).roundToInt()
    }
}
