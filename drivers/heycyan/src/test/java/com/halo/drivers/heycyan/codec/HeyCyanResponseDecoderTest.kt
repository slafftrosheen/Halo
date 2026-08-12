package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HeyCyanResponseDecoderTest {

    private val decoder = HeyCyanResponseDecoder()

    private fun frame(action: Byte, vararg payload: Byte): ByteArray {
        val len = payload.size
        val buf = ByteBuffer.allocate(6 + len).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0xBC.toByte())
        buf.put(action)
        buf.putShort(len.toShort())
        buf.putShort(0xA001.toShort()) // Dummy CRC
        buf.put(payload)
        return buf.array()
    }

    @Test
    fun `decodes battery`() {
        val f = frame(HeyCyanCommandCatalog.ACTION_GLASSES_BATTERY, 85.toByte(), 1.toByte())
        val response = decoder.decode(f)
        assertEquals(HeyCyanResponse.Battery(level = 85, charging = true), response)
    }

    @Test
    fun `decodes volume control`() {
        // frame size = 6 (header) + 14 (payload) = 20
        // payload index 2 = frame index 8
        val payload = ByteArray(14)
        payload[2] = 1 // musicMin
        payload[3] = 15 // musicMax
        payload[4] = 7 // musicCurrent
        payload[6] = 2 // callMin
        payload[7] = 15 // callMax
        payload[8] = 10 // callCurrent
        payload[10] = 3 // systemMin
        payload[11] = 15 // systemMax
        payload[12] = 5 // systemCurrent
        payload[13] = 4 // volumeType
        
        val f = frame(HeyCyanCommandCatalog.ACTION_VOLUME_CONTROL, *payload)
        val response = decoder.decode(f)
        
        assertEquals(
            HeyCyanResponse.VolumeControl(
                musicMin = 1, musicMax = 15, musicCurrent = 7,
                callMin = 2, callMax = 15, callCurrent = 10,
                systemMin = 3, systemMax = 15, systemCurrent = 5,
                volumeType = 4
            ),
            response
        )
    }

    @Test
    fun `decodes media counts`() {
        // payload index 1 = frame index 7 -> dataType 4
        val payload = ByteArray(8)
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(1, 4.toByte()) // dataType
        buf.putShort(2, 10.toShort()) // images
        buf.putShort(4, 20.toShort()) // videos
        buf.putShort(6, 30.toShort()) // records
        
        val f = frame(HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL, *payload)
        val response = decoder.decode(f)
        
        assertEquals(
            HeyCyanResponse.GlassControl.MediaCounts(images = 10, videos = 20, records = 30),
            response
        )
    }

    @Test
    fun `decodes P2P IP bug workaround`() {
        val payload = ByteArray(8)
        payload[1] = 3.toByte() // dataType
        payload[4] = 192.toByte() // frame[10]
        payload[5] = 168.toByte() // frame[11]
        payload[6] = 49.toByte() // frame[12]
        payload[7] = 1.toByte() // frame[13]
        
        val f = frame(HeyCyanCommandCatalog.ACTION_GLASSES_CONTROL, *payload)
        val response = decoder.decode(f)
        
        assertEquals(
            HeyCyanResponse.GlassControl.P2pIp("192.168.49.1"),
            response
        )
    }

    @Test
    fun `invalid magic byte returns null`() {
        val f = frame(HeyCyanCommandCatalog.ACTION_GLASSES_BATTERY, 85.toByte(), 1.toByte())
        f[0] = 0xBD.toByte() // Incorrect magic
        assertNull(decoder.decode(f))
    }
}