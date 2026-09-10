package com.halo.sdk.runtime.embodiment

import com.halo.sdk.runtime.*
import com.halo.sdk.runtime.embodiment.heycyan.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class HeyCyanSessionTest {

    private lateinit var identity: DeviceIdentity
    private lateinit var transport: FakeTransport
    private lateinit var controller: FakeController
    private lateinit var eventBus: FakeEventBus

    @Before
    fun setUp() {
        identity = DeviceIdentity("test-id", "HeyCyan", "glasses")
        transport = FakeTransport()
        controller = FakeController()
        eventBus = FakeEventBus()
    }

    @Test
    fun sessionLifecycleTransitionsToReadyAfterInitialSync() = runTest {
        val session = HeyCyanSession(identity, controller, transport, eventBus, coroutineContext = UnconfinedTestDispatcher(testScheduler))

        transport.emitState(TransportConnectionState.CONNECTED)
        advanceUntilIdle()

        assertEquals(SessionState.READY, session.state.value)
        assertEquals(3, controller.executionCount.get())
    }

    @Test
    fun dispatchMapsIntentsToOperationsAndWaitsForCompletion() = runTest {
        val session = HeyCyanSession(identity, controller, transport, eventBus, coroutineContext = UnconfinedTestDispatcher(testScheduler))
        transport.emitState(TransportConnectionState.CONNECTED)
        advanceUntilIdle()

        val intent = EmbodimentIntent(
            intentId = "intent-1",
            kind = IntentKind.CAPTURE_PHOTO,
            targetDeviceId = "test-id"
        )

        session.dispatch(intent)

        // The runtime contract is that an observation belongs to the concrete
        // device identity used by the session. Do not use the old generic
        // "heycyan" source id here; that would hide a real routing bug.
        eventBus.publish(ObservationUpdated(
            observation = Observation(
                modality = Modality.SYSTEM,
                source = Source.BLUETOOTH,
                payload = SystemObservationPayload("MODE_CHANGED"),
                sourceDeviceId = "test-id",
                metadata = mapOf("event" to "MODE_CHANGED", "mode" to 0x01)
            )
        ))

        advanceUntilIdle()

        val succeededEvent = eventBus.events.find {
            (it as? EmbodimentExecutionEvent)?.result?.status == ExecutionStatus.SUCCEEDED
        } as? EmbodimentExecutionEvent
        assertEquals("intent-1", succeededEvent?.result?.intentId)
    }

    @Test
    fun dispatchTimeoutsIfNoCompletionReceived() = runTest {
        val session = HeyCyanSession(identity, controller, transport, eventBus, coroutineContext = UnconfinedTestDispatcher(testScheduler))
        transport.emitState(TransportConnectionState.CONNECTED)
        advanceUntilIdle()

        val intent = EmbodimentIntent(
            intentId = "intent-timeout",
            kind = IntentKind.CAPTURE_PHOTO,
            targetDeviceId = "test-id"
        )

        session.dispatch(intent)
        advanceUntilIdle()

        testScheduler.advanceTimeBy(11000)
        advanceUntilIdle()

        val failedEvent = eventBus.events.find {
            (it as? EmbodimentExecutionEvent)?.result?.status == ExecutionStatus.FAILED
        } as? EmbodimentExecutionEvent
        assertEquals("intent-timeout", failedEvent?.result?.intentId)
    }

    @Test
    fun modeChangedFromAnotherDeviceDoesNotCompletePhoto() = runTest {
        val session = HeyCyanSession(identity, controller, transport, eventBus, coroutineContext = UnconfinedTestDispatcher(testScheduler))
        transport.emitState(TransportConnectionState.CONNECTED)
        advanceUntilIdle()

        val intent = EmbodimentIntent(
            intentId = "intent-1",
            kind = IntentKind.CAPTURE_PHOTO,
            targetDeviceId = "test-id"
        )

        session.dispatch(intent)

        eventBus.publish(
            ObservationUpdated(
                observation = Observation(
                    modality = Modality.SYSTEM,
                    source = Source.BLUETOOTH,
                    payload = SystemObservationPayload("MODE_CHANGED"),
                    sourceDeviceId = "other-device-id",
                    metadata = mapOf("event" to "MODE_CHANGED", "mode" to 1)
                )
            )
        )

        advanceUntilIdle()

        val succeededEvent = eventBus.events.find {
            (it as? EmbodimentExecutionEvent)?.result?.status == ExecutionStatus.SUCCEEDED
        }
        assertEquals(null, succeededEvent)
    }

    @Test
    fun modeChangedFromThisDeviceCompletesPhoto() = runTest {
        val session = HeyCyanSession(identity, controller, transport, eventBus, coroutineContext = UnconfinedTestDispatcher(testScheduler))
        transport.emitState(TransportConnectionState.CONNECTED)
        advanceUntilIdle()

        val intent = EmbodimentIntent(
            intentId = "intent-1",
            kind = IntentKind.CAPTURE_PHOTO,
            targetDeviceId = "test-id"
        )

        session.dispatch(intent)

        eventBus.publish(
            ObservationUpdated(
                observation = Observation(
                    modality = Modality.SYSTEM,
                    source = Source.BLUETOOTH,
                    payload = SystemObservationPayload("MODE_CHANGED"),
                    sourceDeviceId = "test-id",
                    metadata = mapOf("event" to "MODE_CHANGED", "mode" to 1)
                )
            )
        )

        advanceUntilIdle()

        val succeededEvent = eventBus.events.find {
            (it as? EmbodimentExecutionEvent)?.result?.status == ExecutionStatus.SUCCEEDED
        } as? EmbodimentExecutionEvent
        assertEquals("intent-1", succeededEvent?.result?.intentId)
    }

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
        val events = mutableListOf<HaloEvent>()
        private val _stream = MutableSharedFlow<HaloEvent>()

        override suspend fun publish(event: HaloEvent) {
            events.add(event)
            _stream.emit(event)
        }

        override fun events(): Flow<HaloEvent> = _stream

        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> =
            _stream.filter { type.isInstance(it) }.map { it as T }
    }
}
