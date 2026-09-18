package com.halo.app.ui.screens.discovery

import com.halo.sdk.runtime.DeviceIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class PairingIdentityTest {

    @Test
    fun canonicalIdentityRepairsExistingWrongType() {
        val existing = DeviceIdentity(
            deviceId = "AA:BB",
            deviceType = "BW-AG1 Pro",
            displayName = "HEYCYAN_GLASSES"
        )

        val repaired = canonicalHeyCyanIdentity(
            address = "AA:BB",
            displayName = "My HeyCyan",
            existing = existing
        )

        assertEquals("AA:BB", repaired.deviceId)
        assertEquals("HEYCYAN_GLASSES", repaired.deviceType)
        assertEquals("My HeyCyan", repaired.displayName)
    }

    @Test
    fun canonicalIdentityUsesCanonicalTypeForNewPair() {
        val identity = canonicalHeyCyanIdentity(
            address = "11:22",
            displayName = "BW-AG1 Pro"
        )

        assertEquals("HEYCYAN_GLASSES", identity.deviceType)
        assertEquals("BW-AG1 Pro", identity.displayName)
    }
}
