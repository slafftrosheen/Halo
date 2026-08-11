package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class HeyCyanEventRouterTest {

    private val router = HeyCyanEventRouter(
        sdkDecoder = HeyCyanSdkEventDecoder()
    )

    @Test
    fun `de5bf729 packets remain unknown until channel decoder is verified`() {
        val payload = byteArrayOf(
            0xBC.toByte(),
            0x59.toByte(),
            0x28.toByte(),
            0x00.toByte(),
            0x52.toByte(),
            0x48.toByte(),
            0x4B.toByte(),
            0x41.toByte()
        )

        val event = router.decode(
            UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7"),
            payload
        )

        assertTrue(event is HeyCyanEvent.Unknown)

        val unknown = event as HeyCyanEvent.Unknown

        assertEquals(
            "de5bf729-d711-4e47-af26-65e3012a5dc7",
            unknown.characteristicUuid
        )

        // Verify payload is preserved byte-for-byte
        assertEquals(payload.size, unknown.payload.size)
        for (i in payload.indices) {
            assertEquals(
                "Mismatch at byte $i",
                payload[i].toInt() and 0xFF,
                unknown.payload[i].toInt() and 0xFF
            )
        }
    }

    @Test
    fun `sdk decoded events pass through correctly`() {
        val batteryPayload = byteArrayOf(0x01, 85.toByte(), 0x01)

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