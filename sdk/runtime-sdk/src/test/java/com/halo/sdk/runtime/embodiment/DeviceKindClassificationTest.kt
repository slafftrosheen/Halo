package com.halo.sdk.runtime.embodiment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** #66: identity selection cannot send foreign devices to a HeyCyan transport. */
class DeviceKindClassificationTest {
    @Test fun colmiAndLegacyHeyCyanRemainOnExistingDriver() {
        listOf("HEYCYAN_GLASSES", "HeyCyan", "hey_cyan", "COLMI A2",
            "COLMI_A2", "BW-AG1 Pro").forEach {
            assertEquals(it, DeviceKind.HEYCYAN_GLASSES, DeviceKind.fromDeviceType(it))
        }
    }

    @Test fun everyFerSaiyanVendorHasIndependentFamilyIdentity() {
        val examples = mapOf(
            "EyeVue glasses" to DeviceKind.EYEVUE_GLASSES,
            "AB Mate" to DeviceKind.TUNEBUDS_GLASSES,
            "Meizu MYVU" to DeviceKind.MYVU_GLASSES,
            "MoYoung W620" to DeviceKind.MOYOUNG_W620_GLASSES,
            "Meta Rayban" to DeviceKind.META_RAYBAN_GLASSES,
            "Mentra Live" to DeviceKind.MENTRA_LIVE_GLASSES,
            "MemoMind" to DeviceKind.MEMOMIND_GLASSES,
            "Bluetooth audio" to DeviceKind.GENERIC_AUDIO,
            "BuddyBot" to DeviceKind.BUDDYBOT,
        )
        examples.forEach { (type, family) ->
            assertEquals(type, family, DeviceKind.fromDeviceType(type))
        }
    }

    @Test fun arbitraryGlassesDoNotInheritHeyCyanCapabilities() {
        listOf("Unknown Glasses", "AR glasses", "Camera Glasses", "My Glasses",
            "", "Ray-ban Wayfarer", "EyeVue prototype" ).forEach {
            assertEquals(it, DeviceKind.UNKNOWN, DeviceKind.fromDeviceType(it))
        }
        for (kind in DeviceKind.entries) {
            if (kind == DeviceKind.HEYCYAN_GLASSES || kind == DeviceKind.BUDDYBOT) continue
            val descriptor = DeviceDescriptor.fromKind(kind)
            assertFalse(kind.name, descriptor.capabilities.hasCamera)
            assertFalse(kind.name, descriptor.capabilities.hasMicrophone)
            assertFalse(kind.name, descriptor.capabilities.hasDisplay)
        }
    }
}
