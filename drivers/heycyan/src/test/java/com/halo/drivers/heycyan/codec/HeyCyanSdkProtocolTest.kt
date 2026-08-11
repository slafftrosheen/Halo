package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.test.assertContentEquals

class HeyCyanSdkProtocolTest {

    @Test
    fun `battery event decodes`() {
        val payload = byteArrayOf(
            0x01,
            85.toByte(),
            0x01
        )

        val event = HeyCyanSdkEventDecoder().decode(payload)

        assertEquals(
            HeyCyanEvent.BatteryUpdated(
                level = 85,
                isCharging = true
            ),
            event
        )
    }

    @Test
    fun `media counts decode little endian`() {
        val payload = byteArrayOf(
            0x02,
            0x02, 0x00,       // photos = 2
            0x05, 0x00,       // videos = 5
            0x07, 0x00,       // audio = 7
            0x01              // media type
        )

        val event = HeyCyanSdkEventDecoder().decode(payload)

        assertEquals(
            HeyCyanEvent.MediaCountsUpdated(
                photoCount = 2,
                videoCount = 5,
                audioCount = 7,
                mediaType = 1
            ),
            event
        )
    }

    @Test
    fun `mode event decodes`() {
        val event = HeyCyanSdkEventDecoder()
            .decode(byteArrayOf(0x04, 0x01))

        assertEquals(
            HeyCyanEvent.ModeChanged(1),
            event
        )
    }

    @Test
    fun `de5bf729 packets remain unknown until channel decoder is verified`() {
        val router = HeyCyanEventRouter(
            sdkDecoder = HeyCyanSdkEventDecoder()
        )

        val payload = byteArrayOf(
            0xBC.toByte(),
            0x59,
            0x28,
            0x00,
            0x52,
            0x48,
            0x4B,
            0x41
        )

        val event = router.decode(
            UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7"),
            payload
        )

        assertTrue(event is HeyCyanEvent.Unknown)

        val unknown = event as HeyCyanEvent.Unknown

        assertContentEquals(
            payload,
            unknown.payload
        )
    }
}
