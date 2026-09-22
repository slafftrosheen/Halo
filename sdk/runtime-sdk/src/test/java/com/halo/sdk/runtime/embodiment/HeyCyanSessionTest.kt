package com.halo.sdk.runtime.embodiment

import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.EmbodimentExecutionEvent
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloEvent
import com.halo.sdk.runtime.Modality
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.ObservationUpdated
import com.halo.sdk.runtime.Source
import com.halo.sdk.runtime.SystemObservationPayload
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanController
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanExecutionResult
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeyCyanSessionTest {

    @Test
    fun `recording command is confirmed only by audio mode event`() = runTest {
        val fixture = createFixture(StandardTestDispatcher(testScheduler))
        awaitReady(fixture)

        fixture.session.dispatch(
            EmbodimentIntent(
                intentId = "record-1",
                targetDeviceId = DEVICE_ID,
                kind = IntentKind.START_AUDIO_RECORDING
            )
        )
        runCurrent()

        assertEquals(
            listOf(ExecutionStatus.DISPATCHED),
            fixture.statuses("record-1")
        )

        fixture.events.publish(
            modeObservation(mode = 0x04)
        )
        runCurrent()

        assertEquals(
            listOf(ExecutionStatus.DISPATCHED, ExecutionStatus.SUCCEEDED),
            fixture.statuses("record-1")
        )
        fixture.session.dispose()
    }

    @Test
    fun `recording command without mode confirmation becomes unconfirmed`() = runTest {
        val fixture = createFixture(StandardTestDispatcher(testScheduler))
        awaitReady(fixture)

        fixture.session.dispatch(
            EmbodimentIntent(
                intentId = "record-timeout",
                targetDeviceId = DEVICE_ID,
                kind = IntentKind.START_AUDIO_RECORDING
            )
        )
        runCurrent()
        advanceTimeBy(30_001L)
        runCurrent()

        val statuses = fixture.statuses("record-timeout")
        assertEquals(
            listOf(ExecutionStatus.DISPATCHED, ExecutionStatus.UNCONFIRMED),
            statuses
        )
        assertFalse(statuses.contains(ExecutionStatus.SUCCEEDED))
        assertFalse(statuses.contains(ExecutionStatus.FAILED))
        fixture.session.dispose()
    }

    @Test
    fun `media inventory command is confirmed by media counts event`() = runTest {
        val fixture = createFixture(StandardTestDispatcher(testScheduler))
        awaitReady(fixture)

        fixture.session.dispatch(
            EmbodimentIntent(
                intentId = "inventory-1",
                targetDeviceId = DEVICE_ID,
                kind = IntentKind.FETCH_MEDIA_COUNT
            )
        )
        runCurrent()

        fixture.events.publish(
            ObservationUpdated(
                observation = Observation(
                    modality = Modality.SYSTEM,
                    source = Source.BLUETOOTH,
                    payload = SystemObservationPayload("MEDIA_COUNTS_UPDATED"),
                    sourceDeviceId = DEVICE_ID,
                    metadata = mapOf(
                        "event" to "MEDIA_COUNTS_UPDATED",
                        "photos" to 3,
                        "videos" to 1,
                        "audio" to 2
                    )
                )
            )
        )
        runCurrent()

        assertTrue(fixture.statuses("inventory-1").contains(ExecutionStatus.SUCCEEDED))
        fixture.session.dispose()
    }

    private fun createFixture(dispatcher: CoroutineDispatcher): Fixture {
        val events = FakeEventBus()
        val transport = FakeTransport()
        val controller = FakeController()
        val session = HeyCyanSession(
            identity = DeviceIdentity(DEVICE_ID, "HEY_CYAN", "HeyCyan"),
            controller = controller,
            transport = transport,
            eventBus = events,
            coroutineContext = dispatcher
        )
        return Fixture(session, events)
    }

    private suspend fun TestScope.awaitReady(fixture: Fixture) {
        runCurrent()
        advanceTimeBy(1_100L)
        runCurrent()
        assertEquals(SessionState.READY, fixture.session.state.value)
    }

    private fun Fixture.statuses(intentId: String): List<ExecutionStatus> =
        events.published
            .filterIsInstance<EmbodimentExecutionEvent>()
            .filter { it.result.intentId == intentId }
            .map { it.result.status }

    private fun modeObservation(mode: Int) = ObservationUpdated(
        observation = Observation(
            modality = Modality.SYSTEM,
            source = Source.BLUETOOTH,
            payload = SystemObservationPayload("MODE_CHANGED"),
            sourceDeviceId = DEVICE_ID,
            metadata = mapOf("event" to "MODE_CHANGED", "mode" to mode)
        )
    )

    private data class Fixture(
        val session: HeyCyanSession,
        val events: FakeEventBus
    )

    private class FakeController : HeyCyanController {
        override suspend fun execute(operation: HeyCyanOperation): Result<HeyCyanExecutionResult> =
            Result.success(
                when (operation) {
                    HeyCyanOperation.StartRecording,
                    HeyCyanOperation.StopRecording,
                    HeyCyanOperation.GetMediaInventory -> HeyCyanExecutionResult.Acknowledged
                    else -> HeyCyanExecutionResult.Completed()
                }
            )
    }

    private class FakeTransport : EmbodimentTransport {
        private val mutableState = MutableStateFlow(TransportConnectionState.CONNECTED)
        override val connectionState: StateFlow<TransportConnectionState> = mutableState

        override suspend fun connect() {
            mutableState.value = TransportConnectionState.CONNECTED
        }

        override suspend fun disconnect() {
            mutableState.value = TransportConnectionState.DISCONNECTED
        }

        override suspend fun send(message: String): Boolean = true
        override suspend fun send(bytes: ByteArray): Boolean = true
        override fun onMessageReceived(handler: (String) -> Unit) = Unit
        override fun onBytesReceived(handler: (ByteArray) -> Unit) = Unit
    }

    private class FakeEventBus : EventBus {
        val published = mutableListOf<HaloEvent>()
        private val stream = MutableSharedFlow<HaloEvent>(extraBufferCapacity = 64)

        override suspend fun publish(event: HaloEvent) {
            published += event
            stream.emit(event)
        }

        override fun events(): Flow<HaloEvent> = stream.asSharedFlow()

        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> =
            stream.filter { type.isInstance(it) }.map { type.cast(it) }
    }

    private companion object {
        const val DEVICE_ID = "5F:AE:56:10:38:C2"
    }
}
