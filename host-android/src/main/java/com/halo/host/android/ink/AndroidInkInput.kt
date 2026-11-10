package com.halo.host.android.ink

import android.os.Build
import android.view.InputDevice
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
    private var contact: InkContactIdentity? = null
    private var contactTool: Int? = null
    private var startedAt = 0L
    private var penNearbyUntil = 0L
    private var nearbyDeviceId: Int? = null
    private var isPen = false

    /** Called on layout/mode/lifecycle changes. Incomplete gestures are not durable drafts. */
    fun abort() {
        cancelContact()
        penNearbyUntil = 0; nearbyDeviceId = null; onHover(null)
    }

    private fun cancelContact() { editor.cancel(); contact = null; contactTool = null; isPen = false }

    /** Device removal/change invalidates a contact even if Android sends no ACTION_CANCEL. */
    fun deviceUnavailable(deviceId: Int): Boolean {
        val lostContact = contact?.deviceId == deviceId
        val lostNearbyPen = nearbyDeviceId == deviceId
        if (lostContact) cancelContact()
        if (lostNearbyPen) { nearbyDeviceId = null; penNearbyUntil = 0; onHover(null) }
        if (lostContact || lostNearbyPen) onPreview()
        return lostContact
    }

    fun motion(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val index = event.actionIndex
        val incoming = pointerTool(event, index)
        val begins = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN
        val hovering = action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE
        val owner = contact
        if (owner != null && !owner.ownsStream(event.deviceId, event.downTime)) {
            // An explicit pen arrival can replace an unfinished touch/mouse stroke.
            // Updates/late UP/CANCEL from the old device can never affect its replacement.
            val penWins = !isPen && incoming != null && pen(incoming) && (begins || hovering)
            val newGesture = incoming != null && action == MotionEvent.ACTION_DOWN && event.deviceId == owner.deviceId && event.downTime > owner.downTimeMs
            if (penWins || newGesture) cancelContact() else return false
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            if (contact == null && nearbyDeviceId != event.deviceId) return false
            abort(); onPreview(); return true
        }
        if (event.source and InputDevice.SOURCE_CLASS_POINTER == 0 || event.downTime < 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                if (incoming != null && pen(incoming)) {
                    if (contact != null && !isPen) cancelContact()
                    penNearbyUntil = event.eventTime + 700
                    nearbyDeviceId = event.deviceId
                    onHover(mappedPosition(event.getX(index), event.getY(index)))
                    onPreview()
                }
                return true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                if (nearbyDeviceId == event.deviceId) { nearbyDeviceId = null; penNearbyUntil = 0; onHover(null) }
                return true
            }
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val type = incoming ?: return false
                val incomingPen = pen(type)
                if (event.actionMasked == MotionEvent.ACTION_DOWN) cancelContact()
                if (contact != null) {
                    if (incomingPen && !isPen) cancelContact() // A pen wins over an uncommitted palm/finger.
                    else {
                        if (!isPen && !incomingPen) { cancelContact(); onPreview() }
                        return true // Never connect two pointer IDs into one stroke.
                    }
                }
                if (!incomingPen && type != MotionEvent.TOOL_TYPE_MOUSE && event.eventTime < penNearbyUntil) return true
                if (type == MotionEvent.TOOL_TYPE_MOUSE && event.buttonState and MotionEvent.BUTTON_PRIMARY == 0) return false
                if (mappedPosition(event.getX(index), event.getY(index)) == null) return false
                val tool = when (type) {
                    MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> InkTool.STYLUS
                    MotionEvent.TOOL_TYPE_MOUSE -> InkTool.MOUSE
                    else -> InkTool.FINGER // pointerTool already rejects unsupported/unknown tools.
                }
                val erasing = eraseSelected() || type == MotionEvent.TOOL_TYPE_ERASER ||
                    (incomingPen && event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0)
                if (editor.begin(tool, erasing)) {
                    contact = InkContactIdentity(event.deviceId, event.getPointerId(index), event.downTime)
                    contactTool = type
                    startedAt = event.eventTime; isPen = incomingPen
                    if (isPen) { penNearbyUntil = event.eventTime + 700; nearbyDeviceId = event.deviceId }
                    sample(event, index)
                }
                onHover(null); onPreview(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                val current = contact ?: return false
                val currentIndex = event.findPointerIndex(current.pointerId)
                if (currentIndex < 0 || pointerTool(event, currentIndex) != contactTool ||
                    contactTool == MotionEvent.TOOL_TYPE_MOUSE && event.buttonState and MotionEvent.BUTTON_PRIMARY == 0) {
                    abort(); onPreview(); return true
                }
                if (isPen) penNearbyUntil = event.eventTime + 700
                // Historical samples precede the current sample; timestamps keep that order.
                val start = (event.historySize - InkLimits.MAX_POINTS_PER_STROKE).coerceAtLeast(0)
                for (history in start until event.historySize) sample(event, currentIndex, history)
                sample(event, currentIndex); onPreview(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val current = contact ?: return false
                if (index !in 0 until event.pointerCount || !current.ownsPointer(event.deviceId, event.downTime, event.getPointerId(index))) return true
                val cancelled = Build.VERSION.SDK_INT >= 33 && event.flags and MotionEvent.FLAG_CANCELED != 0
                if (cancelled || pointerTool(event, index) != contactTool) abort() else {
                    sample(event, index)
                    val changed = editor.finish()
                    contact = null; contactTool = null; isPen = false
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
        val mapped = mappedPosition(x, y) ?: return
        fun axis(axis: Int): Float = if (history < 0) event.getAxisValue(axis, index)
            else event.getHistoricalAxisValue(axis, index, history)
        val device = event.device
        val pressureRange = device?.getMotionRange(MotionEvent.AXIS_PRESSURE, event.source)
        val pressure = if (isPen && pressureRange != null)
            normalizePressure(axis(MotionEvent.AXIS_PRESSURE), pressureRange.min, pressureRange.max) else null
        fun angle(which: Int, lower: Float, upper: Float): Int? {
            val range = if (isPen) device?.getMotionRange(which, event.source) else null
            return range?.let { InkInputAxes.angleMilli(axis(which), it.min, it.max, lower, upper) }
        }
        val tilt = angle(MotionEvent.AXIS_TILT, 0f, (Math.PI / 2).toFloat())
        val orientation = angle(MotionEvent.AXIS_ORIENTATION, -Math.PI.toFloat(), Math.PI.toFloat())
        val radius = eraserRadius()
        editor.sample(InkPoint(mapped.first.roundToInt().coerceIn(0, 1000), mapped.second.roundToInt().coerceIn(0, 1000),
            (at - startedAt).coerceIn(0, InkLimits.MAX_CONTACT_MS.toLong()).toInt(), pressure, tilt, orientation,
            event.buttonState and 0xffff), radius.first, radius.second)
    }

    private fun pen(type: Int) = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
    private fun mappedPosition(x: Float, y: Float): Pair<Float, Float>? =
        if (!x.isFinite() || !y.isFinite()) null else position(x, y)?.takeIf { it.first.isFinite() && it.second.isFinite() }

    companion object {
        /** Missing/invalid axes stay unknown; zero pressure is a valid measured value. */
        fun normalizePressure(raw: Float, min: Float, max: Float): Int? =
            InkInputAxes.pressure(raw, min, max)

        /** Mixed touch/stylus sources are valid; relative/joystick coordinates are not canvas pixels. */
        fun pointerTool(event: MotionEvent, index: Int): Int? {
            if (event.source and InputDevice.SOURCE_CLASS_POINTER == 0 || event.downTime < 0 || event.eventTime < event.downTime ||
                index !in 0 until event.pointerCount ||
                event.getPointerId(index) < 0 || !event.getX(index).isFinite() || !event.getY(index).isFinite()) return null
            return when (val tool = event.getToolType(index)) {
                MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER, MotionEvent.TOOL_TYPE_MOUSE -> tool
                MotionEvent.TOOL_TYPE_UNKNOWN -> if (event.isFromSource(InputDevice.SOURCE_MOUSE)) MotionEvent.TOOL_TYPE_MOUSE else null
                else -> null
            }
        }
    }
}
