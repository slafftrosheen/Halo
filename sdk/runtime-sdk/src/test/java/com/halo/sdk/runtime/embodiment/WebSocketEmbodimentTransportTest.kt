package com.halo.sdk.runtime.embodiment

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketEmbodimentTransportTest {

    @Test
    fun initialStateIsDisconnected() {
        val transport = WebSocketEmbodimentTransport("ws://127.0.0.1:8080/ws")
        assertEquals(TransportConnectionState.DISCONNECTED, transport.connectionState.value)
    }

    @Test
    fun invalidUrlTransitionsToError() = runTest {
        val transport = WebSocketEmbodimentTransport("invalid_url://bad")
        transport.connect()
        assertEquals(TransportConnectionState.ERROR, transport.connectionState.value)
    }

    @Test
    fun disconnectResetsStateToDisconnected() = runTest {
        val transport = WebSocketEmbodimentTransport("ws://127.0.0.1:8080/ws")
        transport.disconnect()
        assertEquals(TransportConnectionState.DISCONNECTED, transport.connectionState.value)
    }

    @Test
    fun sendReturnsFalseWhenDisconnected() = runTest {
        val transport = WebSocketEmbodimentTransport("ws://127.0.0.1:8080/ws")
        val sent = transport.send("{\"type\":\"test\"}")
        assertFalse(sent)
    }

    @Test
    fun messageCallbacksCanBeRegistered() {
        val transport = WebSocketEmbodimentTransport("ws://127.0.0.1:8080/ws")
        var received = ""
        transport.onMessageReceived { received = it }
        // Callback registered without exception
        assertTrue(received.isEmpty())
    }
}
