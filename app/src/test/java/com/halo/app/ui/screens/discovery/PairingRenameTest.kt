package com.halo.app.ui.screens.discovery

import com.halo.sdk.runtime.DeviceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingRenameTest {
    @Test fun customNameSurvivesPairingWithNewAdvertisingName() {
        val saved = DeviceIdentity("AA:BB:CC", "HEYCYAN_GLASSES", "My Halo glasses")
        val result = canonicalHeyCyanIdentity("AA:BB:CC", "BW-AG1", saved)
        assertEquals("My Halo glasses", result.displayName)
        assertEquals("HEYCYAN_GLASSES", result.deviceType)
    }
    @Test fun oldConstructorOrderErrorDoesNotOverrideAdvertisement() {
        val legacy = DeviceIdentity("AA:BB", "HEYCYAN_GLASSES", "HEYCYAN_GLASSES")
        assertEquals("BW-AG1", canonicalHeyCyanIdentity("AA:BB", "BW-AG1", legacy).displayName)
    }
    @Test fun nameValidationIsBounded() {
        assertNull(pairingValidDisplayName("   "))
        assertNull(pairingValidDisplayName("x".repeat(49)))
        assertNull(pairingValidDisplayName("Halo\nGlasses"))
        assertEquals("My glasses", pairingValidDisplayName("  My glasses  "))
    }
}
