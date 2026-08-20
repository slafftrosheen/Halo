package com.halo.sdk.runtime.embodiment

import com.halo.sdk.runtime.*
import com.halo.sdk.runtime.embodiment.heycyan.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class HeyCyanSessionTest {

    private lateinit var identity: DeviceIdentity
    private lateinit var transport: FakeTransport
    private lateinit var controller: FakeController
    private lateinit var eventBus: FakeEventBus
    private lateinit var session: HeyCyanSession

    @Before
    fun setUp() {
        identity = DeviceIdentity("test-id", "HeyCyan", "glasses")
        transport = FakeTransport()
        controller = FakeController()
        eventBus = FakeEventBus()
        session = HeyCyanSession(identity, controller, transport, eventBus)
    }

    @Test
    fun sessionLifecycleTransitionsToReadyAfterInitialSync() = runTest {
        assertEquals(SessionState.SAVED, session.state.value)
        
        transport.emitState(TransportConnectionState.CONNECTED)
        
        // Should eventually reach READY
        // Note: syncInitialState has 10s timeout, but in tests it should be fast
        kotlinx.coroutines.delay(500) 
        
        assertEquals(SessionState.READY, session.state.value)
        assertEquals(3, controller.executionCount.get()) // Battery, Info, Media
    }

    @Test
    fun dispatchMapsIntentsToOperations() = runTest {
        transport.emitState(TransportConnectionState.CONNECTED)
        kotlinx.coroutines.delay(500)
        
        val intent = EmbodimentIntent(
            intentId = "intent-1",
            kind = IntentKind.CAPTURE_PHOTO,
            targetDeviceId = "test-id"
        )
        
        session.dispatch(intent)
        kotlinx.coroutines.delay(100)
        
        assertEquals(HeyCyanOperation.TakePhoto, controller.lastOperation)
        
        // Verify event bus publication
        val event = eventBus.lastEvent as? EmbodimentExecutionEvent
        assertEquals("intent-1", event?.result?.intentId)
        assertEquals(ExecutionStatus.ACCEPTED, event?.result?.status)
    }

    // Helper Fakes

    private class FakeTransport : EmbodimentTransport {
        private val _state = MutableStateFlow(TransportConnectionState.DISCONNECTED)
        override val connectionState = _state
        override suspend fun connect() { _state.value = TransportConnectionState.CONNECTING }
        override suspend fun disconnect() { _state.value = TransportConnectionState.DISCONNECTED }
        override suspend fun send(message: String): Boolean = true
        override suspend fun send(bytes: ByteArray): Boolean = true
        override fun onMessageReceived(handler: (String) -> Unit) {}
        override fun onBytesReceived(handler: (ByteArray) -> Unit) {}
        
        fun emitState(state: TransportConnectionState) {
            _state.value = state
        }
    }

    private class FakeController : HeyCyanController {
        val executionCount = AtomicInteger(0)
        var lastOperation: HeyCyanOperation? = null
        
        override suspend fun execute(operation: HeyCyanOperation): Result<HeyCyanExecutionResult> {
            executionCount.incrementAndGet()
            lastOperation = operation
            return Result.success(HeyCyanExecutionResult.Acknowledged)
        }
    }

    private class FakeEventBus : EventBus {
        var lastEvent: Any? = null
        override suspend fun publish(event: Any) { lastEvent = event }
        override fun <T> subscribe(type: Class<T>): kotlinx.coroutines.flow.Flow<T> = kotlinx.coroutines.flow.emptyFlow()
    }
}
