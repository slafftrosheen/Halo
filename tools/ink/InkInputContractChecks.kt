package checks

import com.halo.sdk.runtime.ink.InkContactIdentity
import com.halo.sdk.runtime.ink.InkInputAxes

fun main() {
    var passed = 0
    fun verify(name: String, test: () -> Unit) { test(); passed++; println("PASS $name") }
    val owner = InkContactIdentity(deviceId = 11, pointerId = 0, downTimeMs = 100)
    verify("same pointer ID from another device cannot own the gesture") {
        check(!owner.ownsPointer(22, 100, 0)); check(owner.ownsPointer(11, 100, 0))
    }
    verify("old gesture cannot finish a replacement after numeric IDs are reused") {
        check(!owner.ownsPointer(11, 99, 0)); check(!owner.ownsPointer(11, 101, 0))
    }
    verify("reordered indexes do not change pointer ownership") {
        check(owner.ownsStream(11, 100)); check(!owner.ownsPointer(11, 100, 1))
    }
    verify("invalid pointer and gesture identifiers are rejected") {
        check(runCatching { InkContactIdentity(11, -1, 100) }.isFailure)
        check(runCatching { InkContactIdentity(11, 0, -1) }.isFailure)
    }
    verify("pressure uses real calibration including values above one") {
        check(InkInputAxes.pressure(2f, 0f, 4f) == 500)
        check(InkInputAxes.pressure(0f, 0f, 4f) == 0)
        check(InkInputAxes.pressure(4f, 0f, 4f) == 1000)
    }
    verify("malformed or out-of-range pressure remains unknown") {
        check(InkInputAxes.pressure(Float.NaN, 0f, 1f) == null)
        check(InkInputAxes.pressure(Float.POSITIVE_INFINITY, 0f, 1f) == null)
        check(InkInputAxes.pressure(1f, 1f, 1f) == null)
        check(InkInputAxes.pressure(0f, -Float.MAX_VALUE, Float.MAX_VALUE) == null)
        check(InkInputAxes.pressure(4.1f, 0f, 4f) == null)
    }
    verify("angles retain measured radians without fabricating extreme tilt") {
        check(InkInputAxes.angleMilli(0.5f, 0f, 1.57f, 0f, 1.571f) == 500)
        check(InkInputAxes.angleMilli(-0.5f, -3.142f, 3.142f, -3.142f, 3.142f) == -500)
        check(InkInputAxes.angleMilli(Float.MAX_VALUE, 0f, Float.MAX_VALUE, 0f, 1.571f) == null)
        check(InkInputAxes.angleMilli(0.5f, 1f, 0f, 0f, 1.571f) == null)
        check(InkInputAxes.angleMilli(-0.5f, -1f, 1f, 0f, 1.571f) == null)
    }
    println("Ink input contract checks: $passed passed")
}
