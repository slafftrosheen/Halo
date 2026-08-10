package com.halo.drivers.heycyan.wifi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

/**
 * Fake implementation of [HeyCyanWifiConnection] for unit testing without hardware or Android context.
 */
class FakeHeyCyanWifiConnection : HeyCyanWifiConnection {

    private val _state = MutableStateFlow(WifiConnectionState.DISCONNECTED)
    override val state: StateFlow<WifiConnectionState> = _state.asStateFlow()

    var addressToReturn: InetAddress? = null
    var shouldFailConnect: Boolean = false

    override suspend fun connect(credentials: HeyCyanWifiCredentials): Result<Unit> {
        if (shouldFailConnect) {
            _state.value = WifiConnectionState.FAILED
            return Result.failure(IllegalStateException("Simulated Wi-Fi connection failure"))
        }
        _state.value = WifiConnectionState.CONNECTED
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        _state.value = WifiConnectionState.DISCONNECTED
    }

    override suspend fun currentAddress(): InetAddress? = addressToReturn
}
