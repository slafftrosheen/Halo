package com.halo.app.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class GuidedCapabilitySetupTest {
    @Test
    fun setupRequestsOnlyShippedRuntimeCapabilitiesForThisAndroidVersion() {
        val plan = guidedCapabilityRequests(
            sdk = 33,
            granted = emptySet(),
            askedBefore = emptySet(),
        )

        assertEquals(
            listOf("microphone", "notifications", "contacts", "calendar_read", "calendar_write", "camera", "location", "bluetooth"),
            plan.map { it.id },
        )
        assertEquals(setOf("android.permission.RECORD_AUDIO"), plan.first().permissions)
    }

    @Test
    fun setupOmitsGrantedAndPreviouslyDeclinedCapabilities() {
        val plan = guidedCapabilityRequests(
            sdk = 33,
            granted = setOf("android.permission.RECORD_AUDIO", "android.permission.CAMERA"),
            askedBefore = setOf("notifications", "location"),
        )

        assertEquals(
            listOf("contacts", "calendar_read", "calendar_write", "bluetooth"),
            plan.map { it.id },
        )
    }

    @Test
    fun bluetoothUsesRuntimePermissionsOnlyOnAndroidSAndNewer() {
        val legacy = guidedCapabilityRequests(30, emptySet(), emptySet())
        assertEquals(setOf("android.permission.ACCESS_FINE_LOCATION"), legacy.first { it.id == "bluetooth" }.permissions)

        val current = guidedCapabilityRequests(31, emptySet(), emptySet())
        assertEquals(
            setOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"),
            current.first { it.id == "bluetooth" }.permissions,
        )
    }
}
