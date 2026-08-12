package com.halo.drivers.heycyan

import android.util.Log
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.sdk.host.AudioOutput
import com.halo.sdk.host.AudioSource
import com.halo.sdk.host.ButtonEvent
import com.halo.sdk.host.ConnectionState
import com.halo.sdk.host.DeviceContext
import com.halo.sdk.host.WearableHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*

/**
 * Implementation of [WearableHost] for HeyCyan glasses.
 * Wraps [RealHeyCyanDriver] to expose standard host interfaces.
 */
class HeyCyanHost(
    private val driver: RealHeyCyanDriver,
    override val audioOutput: AudioOutput
) : WearableHost {
    init {
        Log.i("HeyCyanHost", "Initializing HeyCyanHost")
    }
    override val id: String = "heycyan-glasses"
    
    override val audioSource: AudioSource = HeyCyanAudioSource(driver)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var selectedAddress: String? = null

    override val buttons: Flow<ButtonEvent> = driver.heyCyanEvents
        .filterIsInstance<HeyCyanEvent.ButtonPressed>()
        .map {
            // All HeyCyan button interactions trigger cognitive intent
            ButtonEvent.Pressed
        }

    private val _deviceContext = MutableStateFlow(DeviceContext())
    override val deviceContext: StateFlow<DeviceContext> = _deviceContext

    private val _connectivity = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectivity: StateFlow<ConnectionState> = _connectivity

    init {
        // Sync device context from driver state
        driver.deviceState
            .onEach { state ->
                _deviceContext.value = DeviceContext(
                    name = "HeyCyan", // In a real scenario, this comes from BLE Advertisement or Device Info
                    batteryPercent = state.battery?.level,
                    connected = state.connection == com.halo.drivers.heycyan.protocol.HeyCyanConnectionState.READY,
                    charging = state.battery?.charging ?: false,
                    capabilities = emptyList() // The actual capabilities are yet to be decoded
                )
            }
            .launchIn(scope)
            
        // Sync connectivity state
        driver.state
            .onEach { state ->
                _connectivity.value = when(state) {
                    is com.halo.sdk.driver.DriverState.Ready -> ConnectionState.Connected
                    is com.halo.sdk.driver.DriverState.Connecting -> ConnectionState.Connecting
                    is com.halo.sdk.driver.DriverState.Error -> ConnectionState.Failed(state.cause.message ?: "Unknown error")
                    else -> ConnectionState.Disconnected
                }
            }
            .launchIn(scope)
    }

    fun setTargetDevice(address: String) {
        selectedAddress = address
    }

    override suspend fun connect() {
        val address = selectedAddress ?: run {
            Log.w("HeyCyanHost", "Connect called without selected address")
            return
        }
        
        try {
            driver.connect(address, HeyCyanConnectionMode.RUNTIME)
        } catch (e: Exception) {
            Log.e("HeyCyanHost", "Failed to connect to $address", e)
            _connectivity.value = ConnectionState.Failed(e.message ?: "Unknown error")
        }
    }

    override suspend fun disconnect() {
        driver.disconnect()
    }
}
