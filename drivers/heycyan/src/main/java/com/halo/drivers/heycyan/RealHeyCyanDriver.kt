package com.halo.drivers.heycyan

import android.content.Context
import com.halo.sdk.driver.DeviceEvent
import com.halo.sdk.driver.Driver
import com.halo.sdk.driver.DriverState
import com.halo.drivers.heycyan.ble.HeyCyanBleScanner
import com.halo.drivers.heycyan.ble.HeyCyanGattClient
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.drivers.heycyan.protocol.ButtonType
import com.halo.drivers.heycyan.protocol.HeyCyanProtocolParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RealHeyCyanDriver(
    private val context: Context
) : Driver {

    private val scanner = HeyCyanBleScanner(context)
    private val gattClient = HeyCyanGattClient(context)
    private val parser = HeyCyanProtocolParser()
    
    private val _events = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 64)
    override val events: Flow<DeviceEvent> = _events.asSharedFlow()
    
    override val state: StateFlow<DriverState> = gattClient.state
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private var readJob: Job? = null

    override suspend fun initialize() {
        // Any setup before connecting
    }

    override suspend fun connect() {
        // 1. Scan for the device
        val macAddress = try {
            scanner.scanForDevice().first() // Get the first discovered HeyCyan device
        } catch (e: Exception) {
            // Error handling could be expanded here
            return
        }
        
        // 2. Connect to the device via GATT
        gattClient.connect(macAddress)
        
        // 3. Start processing incoming notifications
        startReading()
    }

    override suspend fun disconnect() {
        readJob?.cancel()
        gattClient.disconnect()
    }

    override suspend fun shutdown() {
        disconnect()
    }

    override suspend fun emit(event: DeviceEvent) {
        // For sending events out to the host, but the Driver interface has this method
        _events.emit(event)
    }
    
    private fun startReading() {
        readJob?.cancel()
        readJob = scope.launch {
            gattClient.incomingBytes.collect { payload ->
                val heyCyanEvent = parser.parse(payload) ?: return@collect
                
                // Map HeyCyanEvent to generic DeviceEvent
                val deviceEvent = mapToDeviceEvent(heyCyanEvent)
                if (deviceEvent != null) {
                    _events.emit(deviceEvent)
                }
            }
        }
    }
    
    private fun mapToDeviceEvent(event: HeyCyanEvent): DeviceEvent? {
        val timestamp = System.currentTimeMillis()
        return when (event) {
            is HeyCyanEvent.ButtonPressed -> {
                // Map ButtonType to buttonId (0 for single tap, 1 for double tap, etc)
                val buttonId = when(event.type) {
                    ButtonType.SINGLE_TAP -> 0
                    ButtonType.DOUBLE_TAP -> 1
                    ButtonType.LONG_PRESS -> 2
                    ButtonType.VOICE_BUTTON -> 3
                    ButtonType.CAMERA_BUTTON -> 4
                }
                DeviceEvent.ButtonClick(timestamp, buttonId)
            }
            is HeyCyanEvent.BatteryUpdated -> {
                DeviceEvent.BatteryLevel(timestamp, event.level)
            }
            // Other events like WiFiHotspotReady aren't mapped to generic DeviceEvents yet,
            // they would be handled by specialized channels if necessary.
            else -> null
        }
    }
}
