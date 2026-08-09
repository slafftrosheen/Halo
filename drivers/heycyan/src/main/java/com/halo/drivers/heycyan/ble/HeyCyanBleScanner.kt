package com.halo.drivers.heycyan.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/**
 * Handles discovery of the HeyCyan glasses over BLE.
 */
class HeyCyanBleScanner(private val context: Context) {
    
    companion object {
        val HEYCYAN_SERVICE_UUID: UUID = UUID.fromString("7905FFF0-B5CE-4E99-A40F-4B1E122D00D0")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    private val scanner = adapter.bluetoothLeScanner

    @SuppressLint("MissingPermission") // Caller (HostGraph) is responsible for ensuring permissions
    fun scanForDevice(): Flow<String> = callbackFlow {
        if (scanner == null) {
            close(IllegalStateException("Bluetooth is not enabled or supported"))
            return@callbackFlow
        }

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HEYCYAN_SERVICE_UUID))
            .build()
            
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.address?.let { address ->
                    trySend(address)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(RuntimeException("BLE Scan failed with error code: \$errorCode"))
            }
        }

        scanner.startScan(listOf(scanFilter), scanSettings, callback)

        awaitClose {
            scanner.stopScan(callback)
        }
    }
}
