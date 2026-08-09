package com.halo.drivers.heycyan

import android.content.Context
import com.halo.drivers.heycyan.ble.DiscoveredBleDevice
import com.halo.sdk.driver.DriverState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect

/**
 * Developer workflow for collecting raw HeyCyan evidence.
 *
 * Discovery observes advertisements, while connection always requires an
 * explicitly selected address. No protocol commands are exposed here.
 */
class HeyCyanDiscoverySession(
    context: Context,
    private val driver: RealHeyCyanDriver = RealHeyCyanDriver(context)
) {
    private val _selected = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private var selectedDevice: DiscoveredBleDevice? = null
    val selectedAddress: StateFlow<String?> = _selected.asStateFlow()
    val state: StateFlow<DriverState> = driver.state

    fun scan(): Flow<DiscoveredBleDevice> = flow {
        val seen = mutableSetOf<String>()
        driver.scanner.scan().collect { advertisement ->
            if (seen.add(advertisement.address)) {
                emit(DiscoveredBleDevice(advertisement, System.currentTimeMillis()))
            }
        }
    }

    fun select(device: DiscoveredBleDevice) {
        selectedDevice = device
        _selected.value = device.advertisement.address
    }

    /**
     * Connects only the device selected through [select]. Keeping this as the
     * sole connection entry point prevents a scan result from being connected
     * implicitly by a caller that bypasses the selection workflow.
     */
    suspend fun connectSelected(scenario: String = "discovery") {
        val device = selectedDevice
            ?: throw IllegalStateException("Select a BLE device before connecting")
        driver.connect(
            selectedAddress = device.advertisement.address,
            mode = HeyCyanConnectionMode.DISCOVERY,
            selectedName = device.advertisement.name,
            scenario = scenario,
            advertisement = device.advertisement
        )
    }

    suspend fun disconnect() {
        driver.disconnect()
        selectedDevice = null
        _selected.value = null
    }

    suspend fun shutdown() {
        driver.shutdown()
        selectedDevice = null
        _selected.value = null
    }
}
