package com.halo.drivers.heycyan.transport.rfcomm

import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
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
    }

    @Test
    fun requestSendsEncodedPacketAndCorrelatesResponse() = runTest {
        session = DefaultHeyCyanRfcommSession(
            transport = transport,
            mapper = DefaultHeyCyanRfcommCommandMapper(),
            encoder = encoder,
            scope = backgroundScope
        )

        // Emit response matching GetBattery (0x13) when transport receives write
        val responsePacket = MemoMindPacket(command = 0x13, payload = byteArrayOf(0x50))
        val encodedResponse = encoder.encode(responsePacket)

        // Launch response emission asynchronously in backgroundScope
        backgroundScope.launch {
            // Wait for session.request to be ready
            kotlinx.coroutines.delay(10)
            // Simulate incoming packet from device
            transport.emitIncoming(encodedResponse)
        }

        val result = session.request(HeyCyanCommand.GetBattery, timeout = 2.seconds)
        if (result.isFailure) {
            throw result.exceptionOrNull()!!
        }
        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals(0x13, response.packet.command)
        assertEquals(1, response.packet.payload.size)
        assertEquals(0x50.toByte(), response.packet.payload[0])
    }
}
