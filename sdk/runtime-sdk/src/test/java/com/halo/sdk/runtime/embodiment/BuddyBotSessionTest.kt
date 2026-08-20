package com.halo.sdk.runtime.embodiment

import com.halo.sdk.runtime.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BuddyBotSessionTest {

    private lateinit var identity: DeviceIdentity
    private lateinit var transport: FakeTransport
    private lateinit var eventBus: FakeEventBus

    @Before
    fun setUp() {
        identity = DeviceIdentity("buddy-id", "BuddyBot", "robot")
        transport = FakeTransport()
        eventBus = FakeEventBus()
    }

    @Test
    fun sessionSendsHandshakeOnConnect() = runTest {
        val session = BuddyBotSession(identity, transport, eventBus, coroutineContext = UnconfinedTestDispatcher(testScheduler))
        transport.emitState(TransportConnectionState.CONNECTED)
        advanceUntilIdle()
        
        assertTrue(transport.sentMessages.any { it.contains("\"type\":\"handshake\"") && it.contains("\"v\":1") })
    }

    @Test
    fun sessionBecomesReadyOnHandshakeAck() = runTest {
        val session = BuddyBotSession(identity, transport, eventBus, coroutineContext = UnconfinedTestDispatcher(testScheduler))
        transport.emitState(TransportConnectionState.CONNECTED)
        advanceUntilIdle()
        
        transport.receiveMessage("{\"type\":\"handshake_ack\"}")
        advanceUntilIdle()
        
        assertEquals(SessionState.READY, session.state.value)
    }

    @Test
    fun sessionHandlesVariousExecutionStatuses() = runTest {
        val session = BuddyBotSession(identity, transport, eventBus, coroutineContext = UnconfinedTestDispatcher(testScheduler))
        transport.emitState(TransportConnectionState.CONNECTED)
        transport.receiveMessage("{\"type\":\"handshake_ack\"}")
        advanceUntilIdle()
        
        val statuses = listOf("SUCCEEDED", "FAILED", "DENIED", "REJECTED", "ABORTED")
        
        for (statusStr in statuses) {
            val intentId = "intent-$statusStr"
            transport.receiveMessage("{\"type\":\"exec_result\", \"intentId\":\"$intentId\", \"status\":\"$statusStr\"}")
            advanceUntilIdle()
            
            val event = eventBus.events.find { (it as? EmbodimentExecutionEvent)?.result?.intentId == intentId } as? EmbodimentExecutionEvent
            assertEquals(statusStr, event?.result?.status?.name)
        }
    }

    @Test
    fun sessionPreventsDuplicateIntents() = runTest {
        val session = BuddyBotSession(identity, transport, eventBus, coroutineContext = UnconfinedTestDispatcher(testScheduler))
        transport.emitState(TransportConnectionState.CONNECTED)
        transport.receiveMessage("{\"type\":\"handshake_ack\"}")
        advanceUntilIdle()
        
        val intent = EmbodimentIntent(
            intentId = "intent-dup",
            kind = IntentKind.MOVE,
            driveMode = DriveMode.FORWARD
        )
        
        session.dispatch(intent)
        session.dispatch(intent) // Duplicate
        advanceUntilIdle()
        
        assertEquals(1, transport.sentMessages.count { it.contains("intent-dup") })
    }

    private class FakeTransport : EmbodimentTransport {
        private val _state = MutableStateFlow(TransportConnectionState.DISCONNECTED)
        override val connectionState = _state
        val sentMessages = mutableListOf<String>()
        private var messageHandler: ((String) -> Unit)? = null

        override suspend fun connect() { _state.value = TransportConnectionState.CONNECTING }
        override suspend fun disconnect() { _state.value = TransportConnectionState.DISCONNECTED }
        override suspend fun send(message: String): Boolean {
            sentMessages.add(message)
            return true
        }
        override suspend fun send(bytes: ByteArray): Boolean = true
        override fun onMessageReceived(handler: (String) -> Unit) {
            messageHandler = handler
        }
        override fun onBytesReceived(handler: (ByteArray) -> Unit) {}
        
        fun emitState(state: TransportConnectionState) {
            _state.value = state
        }
        
        fun receiveMessage(message: String) {
            messageHandler?.invoke(message)
        }
    }

    private class FakeEventBus : EventBus {
        val events = mutableListOf<HaloEvent>()
        override suspend fun publish(event: HaloEvent) { events.add(event) }
        override fun events(): Flow<HaloEvent> = emptyFlow()
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
    }
}
