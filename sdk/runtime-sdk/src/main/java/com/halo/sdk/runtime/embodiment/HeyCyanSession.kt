package com.halo.sdk.runtime.embodiment

import com.halo.sdk.runtime.DeviceIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A semantic session for HeyCyan. Wraps a raw transport (like BLE).
 */
class HeyCyanSession(
    override val identity: DeviceIdentity,
    private val transport: EmbodimentTransport
) : EmbodimentSession {

    private val _state = MutableStateFlow(SessionState.SAVED)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    override suspend fun connect() {
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
        
        // Translate semantic intent to HeyCyan BLE characteristic writes.
        // E.g., construct byte array for TTS and send via transport
        transport.send(byteArrayOf(0x01, 0x02))
    }
}
