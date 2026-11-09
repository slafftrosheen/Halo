package com.halo.drivers.heycyan.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.halo.drivers.heycyan.protocol.HeyCyanProtocolConstants

class HeyCyanBleScanEvidenceTest {
    private fun advertisement(
        name: String? = null, uuids: List<String> = emptyList(),
        data: Map<String, String> = emptyMap(), rssi: Int = -65
    ) = BleAdvertisement("AA:BB:CC:DD:EE:FF", name, rssi, uuids, data)

    @Test fun scanResponseEnrichesFirstPacketWithoutDroppingServiceOrName() {
        val first = advertisement(data = mapOf("1234" to "0aff"))
        val withName = mergeBleAdvertisements(first, advertisement(name = "COLMI A2", rssi = -58))
        val enriched = mergeBleAdvertisements(withName,
            advertisement(uuids = listOf(HeyCyanProtocolConstants.PRIMARY_SERVICE.toString())))
        assertEquals("COLMI A2", enriched.name)
        assertEquals(mapOf("1234" to "0aff"), enriched.manufacturerData)
        assertEquals(1, enriched.serviceUuids.size)
        assertTrue(bleAdvertisementEvidenceChanged(first, withName))
        assertTrue(bleAdvertisementEvidenceChanged(withName, enriched))
        assertFalse(bleAdvertisementEvidenceChanged(enriched, enriched.copy(rssi = -50)))
    }

    @Test fun namedNonHeyCyanDevicesUseIndependentFamilies() {
        assertEquals(com.halo.sdk.runtime.embodiment.DeviceKind.HEYCYAN_GLASSES,
            inferGlassesFamilyFromAdvertisement(advertisement(name = "COLMI A2")))
        assertEquals(com.halo.sdk.runtime.embodiment.DeviceKind.EYEVUE_GLASSES,
            inferGlassesFamilyFromAdvertisement(advertisement(
                uuids = listOf("0000aa12-0000-1000-8000-00805f9b34fb"))))
        assertEquals(com.halo.sdk.runtime.embodiment.DeviceKind.TUNEBUDS_GLASSES,
            inferGlassesFamilyFromAdvertisement(advertisement(
                uuids = listOf("0000fdb3-0000-1000-8000-00805f9b34fb"))))
        assertEquals(com.halo.sdk.runtime.embodiment.DeviceKind.MYVU_GLASSES,
            inferGlassesFamilyFromAdvertisement(advertisement(name = "MYVU Star Air")))
        assertEquals(com.halo.sdk.runtime.embodiment.DeviceKind.META_RAYBAN_GLASSES,
            inferGlassesFamilyFromAdvertisement(advertisement(name = "Ray-Ban Meta")))
        assertEquals(com.halo.sdk.runtime.embodiment.DeviceKind.UNKNOWN,
            inferGlassesFamilyFromAdvertisement(advertisement(name = "Unknown Glasses")))
    }

    @Test fun colmiNamesAreOnlyCandidatesNotProtocolProof() {
        assertTrue(heyCyanCandidateLabel(advertisement(name = "COLMI-A2")).contains("candidate"))
        assertTrue(heyCyanCandidateLabel(advertisement(name = "COLMI A2")).contains("candidate"))
        assertTrue(heyCyanCandidateLabel(advertisement(name = "Kitchen sensor")).contains("Unverified"))
        assertTrue(heyCyanCandidateLabel(advertisement(
            uuids = listOf(HeyCyanProtocolConstants.PRIMARY_SERVICE.toString()))).contains("verify"))
    }
}
