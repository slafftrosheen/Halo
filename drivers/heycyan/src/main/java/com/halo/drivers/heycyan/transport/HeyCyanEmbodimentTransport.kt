package com.halo.drivers.heycyan.transport

import com.halo.drivers.heycyan.HeyCyanConnectionMode
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.sdk.driver.DriverState
import com.halo.sdk.runtime.embodiment.EmbodimentTransport
import com.halo.sdk.runtime.embodiment.TransportConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HeyCyanEmbodimentTransport(
    private val driver: RealHeyCyanDriver,
    private val address: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : EmbodimentTransport {

    private val _connectionState = MutableStateFlow(TransportConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<TransportConnectionState> = _connectionState.asStateFlow()

    init {
        scope.launch {
            driver.state.collect { driverState ->
                _connectionState.value = when (driverState) {
                    is DriverState.Ready -> TransportConnectionState.CONNECTED
                    is DriverState.Connecting, is DriverState.Scanning -> TransportConnectionState.CONNECTING
                    is DriverState.Error -> TransportConnectionState.ERROR
                    else -> TransportConnectionState.DISCONNECTED
                }
            }
        }
    }

    override suspend fun connect() {
        driver.connect(address, HeyCyanConnectionMode.RUNTIME)
    }

    override suspend fun disconnect() {
        driver.disconnect()
    }

    override suspend fun send(message: String): Boolean = false

    override suspend fun send(bytes: ByteArray): Boolean = false

    override fun onMessageReceived(handler: (String) -> Unit) {}

    override fun onBytesReceived(handler: (ByteArray) -> Unit) {}
}
