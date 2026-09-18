package com.halo.app.embodiment

import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.embodiment.DeviceKind
import com.halo.sdk.runtime.embodiment.DeviceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAutoConnectCoordinatorTest {

    @Test
    fun selectsOnlyEnabledHeyCyanRecords() {
        val records = listOf(
            DeviceRecord(
                identity = DeviceIdentity(
                    deviceId = "AA:BB",
                    deviceType = "HEYCYAN_GLASSES",
                    displayName = "My Glasses"
                ),
                autoConnect = true
            ),
            DeviceRecord(
                identity = DeviceIdentity(
                    deviceId = "CC:DD",
                    deviceType = "HEYCYAN_GLASSES",
                    displayName = "Spare Glasses"
                ),
                autoConnect = false
            ),
            DeviceRecord(
                identity = DeviceIdentity(
                    deviceId = "buddybot_1",
                    deviceType = "BuddyBot",
                    displayName = "BuddyBot"
                ),
                autoConnect = true
            )
        )

        assertEquals(
            listOf("AA:BB"),
            autoConnectDeviceIds(records, DeviceKind.HEYCYAN_GLASSES)
        )
    }

    @Test
    fun canonicalHeyCyanTypeProducesGlassesDescriptor() {
        val record = DeviceRecord(
            identity = DeviceIdentity(
                deviceId = "11:22",
                deviceType = "HEYCYAN_GLASSES",
                displayName = "Renamed by user"
            )
        )

        assertEquals(DeviceKind.HEYCYAN_GLASSES, record.descriptor.kind)
    }

    @Test
    fun unknownDisplayNameDoesNotOverrideCanonicalDeviceType() {
        val record = DeviceRecord(
            identity = DeviceIdentity(
                deviceId = "11:22",
                deviceType = "HEYCYAN_GLASSES",
                displayName = "Anything"
            )
        )

        val unknown = autoConnectDeviceIds(
            listOf(record),
            DeviceKind.UNKNOWN
        )

        assertTrue(unknown.isEmpty())
    }
    @Test
    fun migratesConstructorOrderBugFromExistingInstall() {
        val broken = DeviceRecord(
            identity = DeviceIdentity(
                deviceId = "AA:BB",
                deviceType = "BW-AG1 Pro",
                displayName = "HEYCYAN_GLASSES"
            ),
            autoConnect = true
        )

        val normalized = normalizeLegacyDeviceRecord(broken)

        assertEquals("HEYCYAN_GLASSES", normalized.identity.deviceType)
        assertEquals("BW-AG1 Pro", normalized.identity.displayName)
        assertEquals(DeviceKind.HEYCYAN_GLASSES, normalized.descriptor.kind)
    }


}
