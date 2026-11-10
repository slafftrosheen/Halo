package com.halo.sdk.runtime.ink

import kotlin.math.roundToInt

/** Pointer IDs are local to a device and reusable in later gestures. */
data class InkContactIdentity(val deviceId: Int, val pointerId: Int, val downTimeMs: Long) {
    init { require(pointerId >= 0 && downTimeMs >= 0) }
    fun ownsStream(device: Int, downTime: Long): Boolean = deviceId == device && downTimeMs == downTime
    fun ownsPointer(device: Int, downTime: Long, pointer: Int): Boolean =
        ownsStream(device, downTime) && pointerId == pointer
}

/** Invalid or unadvertised measurements remain unknown, never synthesized from velocity. */
object InkInputAxes {
    fun pressure(raw: Float, min: Float, max: Float): Int? =
        if (!valid(raw, min, max)) null else (((raw - min) / (max - min)) * 1000).roundToInt().coerceIn(0, 1000)

    fun angleMilli(raw: Float, min: Float, max: Float, lower: Float, upper: Float): Int? =
        if (!valid(raw, min, max) || !lower.isFinite() || !upper.isFinite() || lower > upper || raw !in lower..upper) null
        else (raw * 1000).takeIf { it.isFinite() }?.roundToInt()

    private fun valid(raw: Float, min: Float, max: Float): Boolean =
        raw.isFinite() && min.isFinite() && max.isFinite() && (max - min).isFinite() && max > min && raw in min..max
}
