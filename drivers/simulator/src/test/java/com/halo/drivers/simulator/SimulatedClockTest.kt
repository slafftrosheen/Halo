package com.halo.drivers.simulator

import org.junit.Assert.assertEquals
import org.junit.Test

class SimulatedClockTest {
    @Test
    fun `clock advances only when instructed`() {
        val clock = SimulatedClock(1_000L)

        assertEquals(1_000L, clock.millis())
        clock.advanceBy(250L)
        assertEquals(1_250L, clock.millis())
        clock.setMillis(5_000L)
        assertEquals(5_000L, clock.millis())
    }
}