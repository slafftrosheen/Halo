package com.halo.drivers.heycyan.protocol

import com.halo.sdk.host.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction for the BLE control channel to HeyCyan glasses.
 * This encapsulates scanning, connecting, and the GATT characteristic reads/writes.
 */
interface HeyCyanBleProtocol {
    
    /** Current BLE connection state */
    val connectionState: StateFlow<ConnectionState>
    
    /** Stream of notifications from the device */
    val events: Flow<HeyCyanEvent>
    
    /**
     * Connects to the glasses. 
     * @param identifier MAC address (Android) or UUID (iOS). If null, connects to the first found or previously bound device.
     */
    suspend fun connect(identifier: String? = null)
    
    /** Disconnects from the glasses and tears down the GATT connection. */
    suspend fun disconnect()
    
    /** Sends a command over the Write characteristic. */
    suspend fun sendCommand(command: HeyCyanCommand): Result<Unit>
}
