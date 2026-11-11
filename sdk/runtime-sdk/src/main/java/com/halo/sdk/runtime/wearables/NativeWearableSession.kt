package com.halo.sdk.runtime.wearables

import com.halo.sdk.runtime.*
import com.halo.sdk.runtime.embodiment.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Adapter into the existing runtime session manager, not a parallel device registry. */
class NativeWearableSession(
    override val identity: DeviceIdentity,
    val driver: NativeDeviceDriver,
    private val events: EventBus,
    private val authorizeBond: () -> Unit = {},
) : EmbodimentSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(SessionState.SAVED)
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()
    private val receipts = ConcurrentHashMap<String, ExecutionResult>()
    private val inProgress = ConcurrentHashMap.newKeySet<String>()
    @Volatile var autoReconnectSuppressed = false
        private set

    init {
        scope.launch {
            driver.state.collect { value ->
                mutableState.value = when (value.stage) {
                    NativeStage.DISCONNECTED -> SessionState.DISCONNECTED
                    NativeStage.CONNECTING -> SessionState.CONNECTING
                    NativeStage.HANDSHAKE -> SessionState.HANDSHAKE
                    NativeStage.READY -> SessionState.READY
                    NativeStage.ERROR -> SessionState.ERROR
                }
            }
        }
        scope.launch {
            driver.state.map { Triple(it.stage, it.batteryPercent, it.charging) }.distinctUntilChanged().collect { (stage, battery, charging) ->
                if (battery != null && stage == NativeStage.READY) events.publish(ObservationUpdated(
                    observation = Observation(modality = Modality.SYSTEM, source = Source.BLUETOOTH,
                        payload = BatteryObservationPayload(battery), sourceDeviceId = identity.deviceId,
                        metadata = buildMap {
                            put("battery", battery)
                            put("family", driver.protocolId)
                            charging?.let { put("charging", it) }
                        })))
            }
        }
    }

    fun authorizeExplicitConnection() { autoReconnectSuppressed = false; authorizeBond() }
    override suspend fun connect() {
        check(!autoReconnectSuppressed) { "Explicitly disconnected; request reconnect to continue" }
        driver.connect()
        withTimeout(1_000) { state.first { it == SessionState.READY } }
    }
    override suspend fun disconnect() { autoReconnectSuppressed = true; driver.disconnect() }
    override suspend fun dispose() { disconnect(); driver.dispose(); scope.cancel() }

    override suspend fun dispatch(intent: EmbodimentIntent) {
        if (intent.targetDeviceId != null && intent.targetDeviceId != identity.deviceId) {
            emit(intent.intentId, ExecutionStatus.REJECTED, "Device identity changed"); return
        }
        receipts[intent.intentId]?.let { events.publish(EmbodimentExecutionEvent(device = identity, result = it)); return }
        if (!inProgress.add(intent.intentId)) {
            emit(intent.intentId, ExecutionStatus.REJECTED, "Operation is already in progress; do not replay"); return
        }
        try {
            if (state.value != SessionState.READY) {
                remember(intent.intentId, ExecutionStatus.REJECTED, "Device is not ready"); return
            }
            val action = when (intent.kind) {
                IntentKind.CAPTURE_PHOTO -> NativeAction.PHOTO
                IntentKind.START_VIDEO -> NativeAction.START_VIDEO
                IntentKind.STOP_VIDEO -> NativeAction.STOP_VIDEO
                IntentKind.START_AUDIO_RECORDING -> NativeAction.START_AUDIO
                IntentKind.STOP_AUDIO_RECORDING -> NativeAction.STOP_AUDIO
                IntentKind.FETCH_MEDIA_COUNT -> NativeAction.MEDIA_COUNTS
                else -> null
            }
            if (action == null || action !in driver.supportedActions) { remember(intent.intentId, ExecutionStatus.REJECTED, "Unsupported native capability"); return }
            if (receipts.size + inProgress.size > 4096) {
                emit(intent.intentId, ExecutionStatus.REJECTED, "Session receipt limit reached; reconnect explicitly"); return
            }
            emit(intent.intentId, ExecutionStatus.DISPATCHED, null)
            val receipt = driver.execute(action)
            // Device ACK and a completed physical effect are different facts.
            remember(intent.intentId, ExecutionStatus.UNCONFIRMED,
                "${receipt.confirmation.name}: device-side completion is not confirmed",
                mapOf("nativeReceipt" to receipt.confirmation.name, "family" to driver.protocolId))
        } catch (rejected: NativeDeviceRejected) {
            remember(intent.intentId, ExecutionStatus.FAILED, "Device rejected operation (status ${rejected.status})")
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { remember(intent.intentId, ExecutionStatus.UNCONFIRMED, "Cancelled; do not replay an uncertain physical command") }
            throw cancelled
        } catch (_: Exception) {
            remember(intent.intentId, ExecutionStatus.UNCONFIRMED, "Native transport failed; inspect state before another explicit request")
        } finally { inProgress.remove(intent.intentId) }
    }

    private suspend fun remember(id: String, status: ExecutionStatus, reason: String?, metadata: Map<String, String> = emptyMap()) {
        val result = result(id, status, reason, metadata)
        receipts.putIfAbsent(id, result)
        events.publish(EmbodimentExecutionEvent(device = identity, result = result))
    }
    private suspend fun emit(id: String, status: ExecutionStatus, reason: String?) {
        events.publish(EmbodimentExecutionEvent(device = identity, result = result(id, status, reason)))
    }
    private fun result(id: String, status: ExecutionStatus, reason: String?, metadata: Map<String, String> = emptyMap()) =
        ExecutionResult(intentId = id, status = status, reason = reason, timestamp = System.currentTimeMillis(),
            embodimentId = identity.deviceId, metadata = metadata)
}
