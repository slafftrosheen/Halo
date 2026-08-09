package com.halo.drivers.heycyan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionModePolicyTest {
    @Test
    fun discoveryModeCannotDecodeEventsOrSendCommands() {
        assertFalse(canDecodeEvents(HeyCyanConnectionMode.DISCOVERY))
        assertFalse(canSendCommands(HeyCyanConnectionMode.DISCOVERY))
    }

    @Test
    fun runtimeModeCanDecodeEventsAndSendCommands() {
        assertTrue(canDecodeEvents(HeyCyanConnectionMode.RUNTIME))
        assertTrue(canSendCommands(HeyCyanConnectionMode.RUNTIME))
    }
}
