package com.halo.sdk.runtime.embodiment

import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.EmbodimentExecutionEvent
import com.halo.sdk.runtime.embodiment.ExecutionResult as EmbodimentExecutionResult
import com.halo.sdk.runtime.embodiment.ExecutionStatus as EmbodimentExecutionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * A semantic session for HeyCyan. Wraps a raw transport.
 */
class HeyCyanSession(
    override val identity: DeviceIdentity,
    private val transport: EmbodimentTransport,
    private val eventBus: EventBus
) : EmbodimentSession {

    private val _state = MutableStateFlow(SessionState.SAVED)
    override val state: StateFlow<SessionState> = _state.asStateFlow()
    
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pendingIntents = ConcurrentHashMap<String, Long>()

    init {
        sessionScope.launch {
            transport.connectionState.collect { connState ->
                when (connState) {
                    TransportConnectionState.CONNECTED -> {
                        _state.value = SessionState.CONNECTED
                        _state.value = SessionState.HANDSHAKE
                        _state.value = SessionState.CAPABILITY_SYNC
                        _state.value = SessionState.STATE_SYNC
                        _state.value = SessionState.READY
                    }
                    TransportConnectionState.DISCONNECTED, TransportConnectionState.ERROR -> {
                        val reason = if (connState == TransportConnectionState.ERROR) "TRANSPORT_ERROR" else "DEVICE_DISCONNECTED"
                        failPendingIntents(reason)
                        _state.value = if (connState == TransportConnectionState.ERROR) SessionState.ERROR else SessionState.DISCONNECTED
                    }
                    TransportConnectionState.CONNECTING -> {
                        _state.value = SessionState.CONNECTING
                    }
                }
            }
        }
    }

    private suspend fun failPendingIntents(reason: String) {
        val keys = pendingIntents.keys().toList()
        for (intentId in keys) {
            pendingIntents.remove(intentId)
            eventBus.publish(EmbodimentExecutionEvent(
                device = identity,
                result = EmbodimentExecutionResult(
                    intentId = intentId,
                    status = EmbodimentExecutionStatus.FAILED,
                    reason = reason,
                    timestamp = System.currentTimeMillis(),
                    embodimentId = identity.deviceId
                )
            ))
        }
    }

    override suspend fun connect() {
        if (_state.value == SessionState.CONNECTING || _state.value == SessionState.READY) return
        _state.value = SessionState.CONNECTING
        transport.connect()
    }

    override suspend fun disconnect() {
        _state.value = SessionState.DISCONNECTING
        transport.disconnect()
        _state.value = SessionState.DISCONNECTED
    }

    override suspend fun dispatch(intent: EmbodimentIntent) {
        if (_state.value != SessionState.READY) {
            return
        }
        
        pendingIntents[intent.intentId] = System.currentTimeMillis()
        
        sessionScope.launch {
            pendingIntents.remove(intent.intentId)
            eventBus.publish(EmbodimentExecutionEvent(
                device = identity,
                result = EmbodimentExecutionResult(
                    intentId = intent.intentId,
                    status = EmbodimentExecutionStatus.REJECTED,
                    reason = "UNSUPPORTED_INTENT: HeyCyan semantic mapping not implemented",
                    timestamp = System.currentTimeMillis(),
                    embodimentId = identity.deviceId
                )
            ))
        }
    }
    
    override suspend fun dispose() {
        disconnect()
        failPendingIntents("SESSION_DISPOSED")
        sessionScope.cancel()
    }
}
