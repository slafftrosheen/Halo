package com.halo.drivers.heycyan.transport.rfcomm

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake implementation of [HeyCyanRfcommTransport] for hardware-free unit tests.
 */
class FakeHeyCyanRfcommTransport : HeyCyanRfcommTransport {

    private val _state = MutableStateFlow(RfcommConnectionState.DISCONNECTED)
    override val state: StateFlow<RfcommConnectionState> = _state.asStateFlow()

    val writes = mutableListOf<ByteArray>()

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val incoming = _incoming.asSharedFlow()

    var shouldFailConnect = false

    override suspend fun connect(device: HeyCyanRfcommDevice): Result<Unit> {
        if (shouldFailConnect) {
            _state.value = RfcommConnectionState.FAILED
            return Result.failure(IllegalStateException("Simulated RFCOMM connect failure"))
        }
        _state.value = RfcommConnectionState.CONNECTED
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        _state.value = RfcommConnectionState.DISCONNECTED
    }

    override suspend fun write(bytes: ByteArray): Result<Unit> {
        writes.add(bytes.copyOf())
        return Result.success(Unit)
    }

    suspend fun emitIncoming(bytes: ByteArray) {
        _incoming.emit(bytes)
    }
}
