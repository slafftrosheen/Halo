package com.halo.app.continuity
import com.halo.runtime.cognition.device.DevicePresenceState
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.runtime.embodiment.SessionState
import org.junit.Assert.*
import org.junit.Test
class EndpointEligibilityTest {
    private fun capabilities(id: String = "exact") = listOf(CapabilityInstance(CapabilityId("device.audio.output"), "Audio", "", ToolCategory.DEVICE_CONTROL,
        endpointId = id, availability = CapabilityAvailability.AVAILABLE))
    @Test fun savedAbsentAndAssociatedAreNeverReady() {
        listOf(DevicePresenceState.ASSOCIATED, DevicePresenceState.ABSENT, DevicePresenceState.UNKNOWN, DevicePresenceState.NEARBY).forEach {
            assertFalse(eligible("exact", capabilities(), it, SessionState.READY))
        }
    }
    @Test fun readyRequiresExactCapabilityAndSessionEvidence() {
        assertTrue(eligible("exact", capabilities(), DevicePresenceState.READY, SessionState.READY))
        assertFalse(eligible("exact", capabilities("other"), DevicePresenceState.READY, SessionState.READY))
        assertFalse(eligible("exact", emptyList(), DevicePresenceState.READY, SessionState.READY))
        assertFalse(eligible("exact", capabilities(), DevicePresenceState.READY, null))
    }
}
