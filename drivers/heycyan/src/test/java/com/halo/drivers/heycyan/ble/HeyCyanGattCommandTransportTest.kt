package com.halo.drivers.heycyan.ble

import com.halo.drivers.heycyan.codec.HeyCyanCommand

import com.halo.drivers.heycyan.debug.HeyCyanLogger
import com.halo.drivers.heycyan.protocol.HeyCyanProtocolConstants
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class HeyCyanGattCommandTransportTest {

    @Before
    fun setup() {
        HeyCyanLogger.logger = { tag, msg -> println("$tag: $msg") }
    }

    private class FakeHeyCyanGattClient : HeyCyanGattClient() {
        var lastWrittenUuid: UUID? = null
        var lastWrittenPayload: ByteArray? = null

        override fun write(characteristicUuid: UUID, payload: ByteArray): Boolean {
            lastWrittenUuid = characteristicUuid
            lastWrittenPayload = payload.copyOf()
            return true
        }
    }

    @Test
    fun `photo command encodes to golden vector and targets active write characteristic`() = runBlocking {
        val activeWriteChar = HeyCyanProtocolConstants.WRITE_CHARACTERISTIC
        assertEquals("de5bf72a-d711-4e47-af26-65e3012a5dc7", activeWriteChar.toString())

        val photoFrame = HeyCyanCommand.photo()
        val expectedGoldenVector = byteArrayOf(
            0xBC.toByte(), 0x41.toByte(), 0x03, 0x00, 0x10, 0x50, 0x02, 0x01, 0x01
        )
        assertArrayEquals(expectedGoldenVector, photoFrame)

        val fakeClient = FakeHeyCyanGattClient()

        val transport = HeyCyanGattCommandTransport(
            gatt = fakeClient,
            commandCharacteristic = activeWriteChar
        )

        val result = transport.send(photoFrame)

        assertTrue(result.isSuccess)
        assertEquals(activeWriteChar, fakeClient.lastWrittenUuid)
        assertArrayEquals(expectedGoldenVector, fakeClient.lastWrittenPayload)
    }

}
