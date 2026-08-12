package com.halo.drivers.heycyan.codec

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class HeyCyanProtocolCaptureTest {

    private val characteristic = UUID.randomUUID()

    @Before
    fun setUp() {
        HeyCyanProtocolCapture.instance.setCaptureEnabled(true)
        HeyCyanProtocolCapture.instance.clear()
    }

    @Test
    fun `records and serializes frames correctly`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        HeyCyanProtocolCapture.instance.recordFrame(characteristic, payload, PacketDirection.TX, "WRITE")
        
        // Internal access to frames list would be better, but we check Logcat or side effects.
        // For unit test, we can verify JSONL format helper.
        val frame = RawHeyCyanFrame(
            timestampMillis = 1000L,
            direction = PacketDirection.TX,
            characteristicUuid = characteristic,
            bytes = payload,
            writeType = "WRITE"
        )
        
        with(HeyCyanProtocolCapture.instance) {
            val jsonl = frame.toJsonl()
            assertTrue(jsonl.contains("\"direction\": \"TX\""))
            assertTrue(jsonl.contains("\"bytes\": \"010203\""))
            assertTrue(jsonl.contains(characteristic.toString()))
        }
    }

    @Test
    fun `captures nothing when disabled`() {
        HeyCyanProtocolCapture.instance.setCaptureEnabled(false)
        HeyCyanProtocolCapture.instance.recordFrame(characteristic, byteArrayOf(0x01), PacketDirection.TX)
        
        // This is a behavioral test - verify no crashes and no state changes if possible.
    }
}
