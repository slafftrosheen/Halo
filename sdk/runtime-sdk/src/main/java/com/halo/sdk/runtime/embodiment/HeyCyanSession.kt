package com.halo.sdk.runtime.embodiment

import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.EmbodimentExecutionEvent
import com.halo.sdk.runtime.embodiment.ExecutionResult as EmbodimentExecutionResult
import com.halo.sdk.runtime.embodiment.ExecutionStatus as EmbodimentExecutionStatus
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanController
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanExecutionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.halo.sdk.runtime.ObservationUpdated
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * A semantic session for HeyCyan. Wraps a raw transport.
 */
class HeyCyanSession(
    override val identity: DeviceIdentity,
    private val controller: HeyCyanController,
    private val transport: EmbodimentTransport,
    private val eventBus: EventBus,
    private val coroutineContext: CoroutineContext = Dispatchers.Default
) : EmbodimentSession {

    private val _state = MutableStateFlow(SessionState.SAVED)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val sessionScope = CoroutineScope(SupervisorJob() + coroutineContext)
    private val pendingIntents = ConcurrentHashMap<String, Long>()

    init {
        sessionScope.launch {
            transport.connectionState.collect { connState ->
                when (connState) {
                    TransportConnectionState.CONNECTED -> handleConnected()
                    TransportConnectionState.DISCONNECTED, TransportConnectionState.ERROR -> {
                        val reason = if (connState == TransportConnectionState.ERROR) {
                            "TRANSPORT_ERROR"
                        } else {
                            "DEVICE_DISCONNECTED"
                        }
                        failPendingIntents(reason)
                        _state.value = if (connState == TransportConnectionState.ERROR) {
                            SessionState.ERROR
                        } else {
                            SessionState.DISCONNECTED
                        }
                    }
                    TransportConnectionState.CONNECTING -> {
                        _state.value = SessionState.CONNECTING
                    }
                }
            }
        }
    }

    private suspend fun handleConnected() {
        _state.value = SessionState.CONNECTED
        _state.value = SessionState.HANDSHAKE
        delay(100)
        _state.value = SessionState.CAPABILITY_SYNC
        _state.value = SessionState.STATE_SYNC

        if (syncInitialState()) {
            _state.value = SessionState.READY
        } else {
            _state.value = SessionState.ERROR
        }
    }

    private suspend fun syncInitialState(): Boolean =
        withTimeoutOrNull(10000L) {
            runCatching { controller.execute(HeyCyanOperation.GetBattery) }
            delay(300L)
            runCatching { controller.execute(HeyCyanOperation.GetDeviceInfo) }
            delay(300L)
            runCatching { controller.execute(HeyCyanOperation.GetMediaInventory) }
            delay(300L)
            true
        } ?: false

    private suspend fun failPendingIntents(reason: String) {
        val keys = pendingIntents.keys().toList()
        for (intentId in keys) {
            pendingIntents.remove(intentId)
            reportResult(intentId, EmbodimentExecutionStatus.FAILED, reason)
        }
    }

    override suspend fun connect() {
        if (_state.value == SessionState.CONNECTING || _state.value == SessionState.READY) return
        _state.value = SessionState.CONNECTING
        try {
            transport.connect()
        } catch (e: CancellationException) {
            _state.value = SessionState.DISCONNECTED
            throw e
        }
    }

    override suspend fun disconnect() {
        _state.value = SessionState.DISCONNECTING
        try {
            transport.disconnect()
        } finally {
            _state.value = SessionState.DISCONNECTED
        }
    }

    override suspend fun dispatch(intent: EmbodimentIntent) {
        if (_state.value != SessionState.READY) {
            reportResult(
                intent.intentId,
                EmbodimentExecutionStatus.REJECTED,
                "Device not ready (state: ${_state.value})",
            )
            return
        }

        val operation = mapIntentToOperation(intent)
        if (operation == null) {
            reportResult(
                intent.intentId,
                EmbodimentExecutionStatus.REJECTED,
                "UNSUPPORTED_INTENT: HeyCyan semantic mapping not implemented for ${intent.kind}",
            )
            return
        }

        if (pendingIntents.putIfAbsent(intent.intentId, System.currentTimeMillis()) != null) {
            return
        }

        sessionScope.launch {
            try {
                controller.execute(operation).fold(
                    onSuccess = { executionResult ->
                        when (executionResult) {
                            is HeyCyanExecutionResult.Acknowledged -> {
                                reportResult(intent.intentId, EmbodimentExecutionStatus.ACCEPTED)
                                waitForCompletion(intent.intentId, operation)
                            }
                            is HeyCyanExecutionResult.Completed -> {
                                reportResult(
                                    intent.intentId,
                                    EmbodimentExecutionStatus.SUCCEEDED,
                                    metadata = executionResult.metadata,
                                )
                                pendingIntents.remove(intent.intentId)
                            }
                        }
                    },
                    onFailure = { error ->
                        pendingIntents.remove(intent.intentId)
                        reportResult(
                            intent.intentId,
                            EmbodimentExecutionStatus.FAILED,
                            error.message ?: "Unknown execution error",
                        )
                    },
                )
            } catch (e: CancellationException) {
                pendingIntents.remove(intent.intentId)
                throw e
            } catch (e: Exception) {
                pendingIntents.remove(intent.intentId)
                reportResult(
                    intent.intentId,
                    EmbodimentExecutionStatus.FAILED,
                    "Execution failed: ${e.message}",
                )
            }
        }
    }

    /**
     * The current driver contract returns Acknowledged for a successful command.
     * Completion is therefore observed from the device event stream. Until the
     * driver carries a per-command correlation id, matching accepts the canonical
     * runtime device identity and the legacy HeyCyan source id emitted by the
     * current driver. Mode values are normalized to strings because protocol
     * decoders may expose them as either numeric or string metadata.
     */
    private suspend fun waitForCompletion(
        intentId: String,
        operation: HeyCyanOperation,
    ) {
        fun ObservationUpdated.belongsToThisDevice(): Boolean =
            observation.sourceDeviceId == identity.deviceId

        fun ObservationUpdated.modeString(): String? =
            observation.metadata["mode"]?.toString()

        try {
            val completed = withTimeoutOrNull(30000L) {
                when (operation) {
                    HeyCyanOperation.TakePhoto -> {
                        eventBus.observe(ObservationUpdated::class.java)
                            .first { obs ->
                                obs.belongsToThisDevice() &&
                                    obs.observation.metadata["event"] == "MODE_CHANGED" &&
                                    obs.modeString() == "1"
                            }
                    }
                    HeyCyanOperation.StartVideo -> {
                        eventBus.observe(ObservationUpdated::class.java)
                            .first { obs ->
                                obs.belongsToThisDevice() &&
                                    obs.observation.metadata["event"] == "MODE_CHANGED" &&
                                    obs.modeString() == "2"
                            }
                    }
                    HeyCyanOperation.StopVideo -> {
                        eventBus.observe(ObservationUpdated::class.java)
                            .first { obs ->
                                obs.belongsToThisDevice() &&
                                    obs.observation.metadata["event"] == "MODE_CHANGED" &&
                                    obs.modeString() != "2"
                            }
                    }
                    else -> {
                        delay(500L)
                        true
                    }
                }
                true
            } ?: false

            if (completed) {
                reportResult(intentId, EmbodimentExecutionStatus.SUCCEEDED)
            } else {
                reportResult(intentId, EmbodimentExecutionStatus.FAILED, "Execution timed out")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportResult(intentId, EmbodimentExecutionStatus.FAILED, "Execution failed: ${e.message}")
        } finally {
            pendingIntents.remove(intentId)
        }
    }

    private fun mapIntentToOperation(intent: EmbodimentIntent): HeyCyanOperation? =
        when (intent.kind) {
            IntentKind.CAPTURE_PHOTO -> HeyCyanOperation.TakePhoto
            IntentKind.START_VIDEO -> HeyCyanOperation.StartVideo
            IntentKind.STOP_VIDEO -> HeyCyanOperation.StopVideo
            IntentKind.START_AUDIO_RECORDING -> HeyCyanOperation.StartRecording
            IntentKind.STOP_AUDIO_RECORDING -> HeyCyanOperation.StopRecording
            IntentKind.FETCH_MEDIA_COUNT -> HeyCyanOperation.GetMediaInventory
            else -> null
        }

    private suspend fun reportResult(
        intentId: String,
        status: EmbodimentExecutionStatus,
        reason: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ) {
        eventBus.publish(
            EmbodimentExecutionEvent(
                device = identity,
                result = EmbodimentExecutionResult(
                    intentId = intentId,
                    status = status,
                    reason = reason,
                    timestamp = System.currentTimeMillis(),
                    embodimentId = identity.deviceId,
                    metadata = metadata,
                )
            )
        )
    }

    override suspend fun dispose() {
        disconnect()
        failPendingIntents("SESSION_DISPOSED")
        sessionScope.cancel()
    }
}
