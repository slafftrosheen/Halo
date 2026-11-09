package com.halo.drivers.heycyan.ble

import com.halo.drivers.heycyan.protocol.HeyCyanProtocolConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/** A random BLE GATT service must never reach HeyCyan READY. */
class HeyCyanGattProfileValidatorTest {
    private val writable = CharacteristicProperties(false, false, true, false, false)
    private val notify = CharacteristicProperties(false, false, false, true, false)
    private val nothing = CharacteristicProperties(false, false, false, false, false)

    private fun service(
        write: CharacteristicProperties? = writable,
        report: CharacteristicProperties? = notify,
        uuid: UUID = HeyCyanProtocolConstants.PRIMARY_SERVICE
    ) = DiscoveredService(
        uuid,
        listOfNotNull(
            write?.let { DiscoveredCharacteristic(HeyCyanProtocolConstants.WRITE_CHARACTERISTIC, it) },
            report?.let { DiscoveredCharacteristic(HeyCyanProtocolConstants.NOTIFY_CHARACTERISTIC, it) },
        )
    )

    @Test fun exactControlProfileCanProceedToCccdHandshake() {
        assertNull(HeyCyanGattProfileValidator.rejectionReason(listOf(service())))
    }

    @Test fun arbitraryGattCannotMasqueradeAsHeyCyan() {
        assertEquals("primary HeyCyan service is missing",
            HeyCyanGattProfileValidator.rejectionReason(listOf(
                service(uuid = UUID.fromString("0000aa12-0000-1000-8000-00805f9b34fb")))))
    }

    @Test fun missingOrNonfunctionalCommandEndpointsAreRejected() {
        assertEquals("command-write characteristic is missing",
            HeyCyanGattProfileValidator.rejectionReason(listOf(service(write = null))))
        assertEquals("command-write characteristic has no write permission",
            HeyCyanGattProfileValidator.rejectionReason(listOf(service(write = nothing))))
        assertEquals("command-notification characteristic is missing",
            HeyCyanGattProfileValidator.rejectionReason(listOf(service(report = null))))
        assertEquals("command-notification characteristic cannot notify/indicate",
            HeyCyanGattProfileValidator.rejectionReason(listOf(service(report = nothing))))
    }

    @Test fun resolverNeverChoosesUnrelatedWriteAndNotifyByPropertiesAlone() {
        val otherWrite = UUID.fromString("0000aa13-0000-1000-8000-00805f9b34fb")
        val otherNotify = UUID.fromString("0000aa14-0000-1000-8000-00805f9b34fb")
        val wrong = DiscoveredService(HeyCyanProtocolConstants.PRIMARY_SERVICE, listOf(
            DiscoveredCharacteristic(otherWrite, writable),
            DiscoveredCharacteristic(otherNotify, notify),
        ))
        val resolved = HeyCyanCharacteristicResolver().resolve(listOf(wrong))
        assertNull(resolved.commandWrite)
        assertNull(resolved.notification)
    }
}
