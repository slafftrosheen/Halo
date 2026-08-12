package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class HeyCyanEventRouterTest {

    private val router = HeyCyanEventRouter(
        responseDecoder = HeyCyanResponseDecoder()
    )

    @Test
    fun `de5bf729 packets are recognized as proprietary frames`() {
        val payload = byteArrayOf(
            0xBC.toByte(),
            0x73,
            0x04,
            0x00,
            0x52,
            0x48,
            0x4B,
            0x41,
            0x00,
            0x00
        )

        val event = router.decode(
            UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7"),
            payload
        )

        assertTrue(event is HeyCyanEvent.DecodedProprietaryFrame)

        val frame = event as HeyCyanEvent.DecodedProprietaryFrame

        assertEquals(HeyCyanActionFamily.DEVICE_DATA_REPORTING, frame.actionFamily)
        assertEquals(0x73.toByte(), frame.action)
        assertEquals(4, frame.length)
        assertEquals("BC73040052484B410000", frame.rawHex)
    }

    @Test
    fun `sdk decoded events pass through correctly`() {
        // Construct a valid BC frame for Battery: BC 42 02 00 xx xx 55 01
        // 55 hex = 85 decimal
        val batteryPayload = byteArrayOf(
            0xBC.toByte(),
            0x42,
            0x02,
            0x00,
            0x00, // CRC placeholder
            0x00, // CRC placeholder
            85.toByte(), // level
            0x01 // charging
        )

        val event = router.decode(
            UUID.fromString("0000fed1-0000-1000-8000-00805f9b34fb"),
            batteryPayload
        )

        assertTrue(event is HeyCyanEvent.BatteryUpdated)
        val batteryEvent = event as HeyCyanEvent.BatteryUpdated
        assertEquals(85, batteryEvent.level)
        assertEquals(true, batteryEvent.isCharging)
    }

    @Test
    fun `unknown characteristics route to Unknown event`() {
        val payload = byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())

        val event = router.decode(
            UUID.fromString("00000000-0000-1000-8000-00805f9b34fb"),
            payload
        )

        assertTrue(event is HeyCyanEvent.Unknown)
        val unknown = event as HeyCyanEvent.Unknown
        assertEquals("00000000-0000-1000-8000-00805f9b34fb", unknown.characteristicUuid)
    }
}