package com.halo.drivers.simulator

import com.halo.sdk.runtime.RuntimeClock
import java.time.Instant

/**
 * Deterministic clock for simulator and JVM integration tests.
 *
 * The clock never advances by itself. Tests and tools explicitly move it forward,
 * which makes latency and timeout behavior reproducible.
 */
class SimulatedClock(initialMillis: Long = 0L) : RuntimeClock {
    private var currentMillis = initialMillis

    @Synchronized
    override fun now(): Instant = Instant.ofEpochMilli(currentMillis)

    @Synchronized
    override fun millis(): Long = currentMillis

    @Synchronized
    fun setMillis(millis: Long) {
        require(millis >= 0L) { "Simulated time must not be negative" }
        currentMillis = millis
    }

    @Synchronized
    fun advanceBy(millis: Long) {
        require(millis >= 0L) { "Simulated time can only advance" }
        currentMillis += millis
    }
}