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
            listOf("microphone", "notifications", "contacts", "calendar_read", "calendar_write", "camera", "location", "bluetooth", "nearby_wifi"),
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
            listOf("contacts", "calendar_read", "calendar_write", "bluetooth", "nearby_wifi"),
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

    @Test fun versionBoundariesAndSpecialAccessNeverRequestBroadStorage() {
        assertEquals(false, guidedCapabilityInventory(32).any { it.id == "notifications" || it.id == "nearby_wifi" })
        assertEquals(false, guidedCapabilityInventory(36).any { it.id == "local_network" })
        assertEquals(true, guidedCapabilityInventory(37).any { it.id == "local_network" })
        val special = guidedCapabilityInventory(35).filter { it.special }
        assertEquals(setOf("notification_access", "accessibility", "write_settings", "notification_policy", "overlay", "assistant", "exact_alarm"), special.map { it.id }.toSet())
        assertEquals(true, special.all { it.permissions.isEmpty() })
        assertEquals(false, guidedCapabilityInventory(37).flatMap { it.permissions }.any { "STORAGE" in it || "CALL_PHONE" in it })
    }

    @Test fun locationRequestsCoarseAndFineTogether() {
        assertEquals(setOf("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"),
            guidedCapabilityInventory(35).first { it.id == "location" }.permissions)
    }

    @Test fun actualGrantOverridesReceiptsAndRevocationDoesNotStayAllowed() {
        assertEquals(CapabilityAccessState.ALLOWED, capabilityAccessState(true, true, false, true, false))
        assertEquals(CapabilityAccessState.SETTINGS_REQUIRED, capabilityAccessState(true, false, false, true, false))
        assertEquals(CapabilityAccessState.DECLINED, capabilityAccessState(true, false, false, true, true))
        assertEquals(CapabilityAccessState.NOT_ASKED, capabilityAccessState(true, false, false, false, false))
        assertEquals(CapabilityAccessState.SETTINGS_REQUIRED, capabilityAccessState(true, false, true, false, false))
        assertEquals(CapabilityAccessState.UNAVAILABLE, capabilityAccessState(false, false, false, false, false))
    }

    @Test fun sequenceSkipsGrantedAttemptedSkippedAndUnavailableIncludingInterruptedRequest() {
        val inventory = guidedCapabilityInventory(35)
        assertEquals("camera", nextGuidedCapability(inventory, "microphone", setOf("notifications"),
            setOf("contacts"), setOf("calendar_read"), setOf("calendar_write")))
        assertEquals("notifications", nextGuidedCapability(inventory, null, emptySet(), setOf("microphone"), emptySet(), emptySet()))
        assertEquals(null, nextGuidedCapability(inventory, "exact_alarm", emptySet(), emptySet(), emptySet(), emptySet()))
    }
}
