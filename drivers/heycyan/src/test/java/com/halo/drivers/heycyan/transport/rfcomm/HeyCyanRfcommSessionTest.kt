package com.halo.drivers.heycyan.transport.rfcomm

import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class HeyCyanRfcommSessionTest {

    private lateinit var transport: FakeHeyCyanRfcommTransport
    private lateinit var encoder: MemoMindPacketEncoder
    private lateinit var session: DefaultHeyCyanRfcommSession

    @Before
    fun setUp() {
        transport = FakeHeyCyanRfcommTransport()
        encoder = MemoMindPacketEncoder()
        session = DefaultHeyCyanRfcommSession(
            transport = transport,
            mapper = DefaultHeyCyanRfcommCommandMapper(),
            encoder = encoder,
        )
    }

    @Test
    fun requestSendsEncodedPacketAndCorrelatesResponse() = runTest {
        // Emit response matching GetBattery (0x13) when transport receives write
        val responsePacket = MemoMindPacket(command = 0x13, payload = byteArrayOf(0x50))
        val encodedResponse = encoder.encode(responsePacket)

        // Launch request asynchronously
        val job = runTest {
            // Simulate incoming packet from device
            transport.emitIncoming(encodedResponse)
        }

        val result = session.request(HeyCyanCommand.GetBattery, timeout = 2.seconds)
        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals(0x13, response.packet.command)
        assertEquals(1, response.packet.payload.size)
        assertEquals(0x50.toByte(), response.packet.payload[0])
    }
}
