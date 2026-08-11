package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanMode
import org.junit.Test
import org.junit.Assert.*

class HeyCyanCommandEncoderTest {

    private val encoder = HeyCyanCommandEncoder()

    @Test
    fun photoModeProducesModeByte() {
        val encoded = encoder
            .encode(HeyCyanCommand.SetMode(HeyCyanMode.PHOTO))
            .getOrThrow()

        assertEquals(0x01, encoded.commandId)
        assertArrayEquals(byteArrayOf(0x01), encoded.payload)
    }

    @Test
    fun allKnownModesMapCorrectly() {
        val expected = mapOf(
            HeyCyanMode.PHOTO to 0x01,
            HeyCyanMode.VIDEO to 0x02,
            HeyCyanMode.VIDEO_STOP to 0x03,
            HeyCyanMode.AUDIO to 0x04,
            HeyCyanMode.AUDIO_STOP to 0x05,
            HeyCyanMode.AI_PHOTO to 0x06,
            HeyCyanMode.TRANSFER to 0x07,
        )

        expected.forEach { (mode, value) ->
            val command = encoder
                .encode(HeyCyanCommand.SetMode(mode))
                .getOrThrow()

            assertEquals(
                value.toByte(),
                command.payload.single()
            )
        }
    }

    @Test
    fun getVersionCommand() {
        val encoded = encoder
            .encode(HeyCyanCommand.GetVersion)
            .getOrThrow()

        assertEquals(0x10.toByte(), encoded.commandId.toByte())
        assertArrayEquals(byteArrayOf(), encoded.payload)
    }

    @Test
    fun getMacAddressCommand() {
        val encoded = encoder
            .encode(HeyCyanCommand.GetMacAddress)
            .getOrThrow()

        assertEquals(0x11.toByte(), encoded.commandId.toByte())
        assertArrayEquals(byteArrayOf(), encoded.payload)
    }

    @Test
    fun getBatteryCommand() {
        val encoded = encoder
            .encode(HeyCyanCommand.GetBattery)
            .getOrThrow()

        assertEquals(0x13.toByte(), encoded.commandId.toByte())
        assertArrayEquals(byteArrayOf(), encoded.payload)
    }

    @Test
    fun getMediaCountsCommand() {
        val encoded = encoder
            .encode(HeyCyanCommand.GetMediaCounts)
            .getOrThrow()

        assertEquals(0x14.toByte(), encoded.commandId.toByte())
        assertArrayEquals(byteArrayOf(), encoded.payload)
    }

    @Test
    fun setDateTimeIsSupported() {
        val result = encoder.encode(HeyCyanCommand.SetDateTime(0L))

        assertTrue(result.isSuccess)
        assertEquals(0x12.toByte(), result.getOrThrow().commandId.toByte())
    }

    @Test
    fun encodedCommandIsImmutable() {
        val encoded = encoder
            .encode(HeyCyanCommand.SetMode(HeyCyanMode.VIDEO))
            .getOrThrow()

        // Verify payload is a copy, not a reference
        val originalPayload = encoded.payload
        originalPayload[0] = 0xFF.toByte()

        // Get a new encoded command and verify it's different
        val encoded2 = encoder
            .encode(HeyCyanCommand.SetMode(HeyCyanMode.VIDEO))
            .getOrThrow()

        // Should still be 0x02 (VIDEO), not 0xFF
        assertEquals(0x02.toByte(), encoded2.payload[0])
    }
}