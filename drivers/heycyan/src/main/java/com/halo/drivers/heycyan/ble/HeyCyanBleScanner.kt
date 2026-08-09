package com.halo.drivers.heycyan.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.halo.drivers.heycyan.codec.HexCodec
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Collections

/**
 * Handles discovery of nearby BLE devices.
 *
 * Protocol-specific service UUID filtering is intentionally not used here. During
 * protocol discovery we need to observe the device advertisement first rather than
 * assume that a particular service UUID is correct.
 */
data class BleAdvertisement(
    val address: String,
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<String>,
    val manufacturerData: Map<String, String>
)

class HeyCyanBleScanner(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    private val scanner = adapter.bluetoothLeScanner

    @SuppressLint("MissingPermission") // Caller (HostGraph) is responsible for ensuring permissions
    fun scanForDevice(): Flow<BleAdvertisement> = callbackFlow {
        if (scanner == null) {
            close(IllegalStateException("Bluetooth is not enabled or supported"))
            return@callbackFlow
        }

        val discoveredAddresses = Collections.synchronizedSet(mutableSetOf<String>())
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val scanResult = result ?: return
                val address = scanResult.device?.address ?: return
                if (discoveredAddresses.add(address)) {
                    val record = scanResult.scanRecord
                    val manufacturerData = buildMap {
                        record?.manufacturerSpecificData?.let { data ->
                            for (index in 0 until data.size()) {
                                put(data.keyAt(index).toString(), HexCodec.encode(data.valueAt(index)))
                            }
                        }
                    }
                    trySend(
                        BleAdvertisement(
                            address = address,
                            name = record?.deviceName,
                            rssi = scanResult.rssi,
                            serviceUuids = record?.serviceUuids?.map { it.uuid.toString() } ?: emptyList(),
                            manufacturerData = manufacturerData
                        )
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(RuntimeException("BLE Scan failed with error code: $errorCode"))
            }
        }

        // An empty filter list means that every advertising BLE device is reported.
        scanner.startScan(emptyList(), scanSettings, callback)

        awaitClose {
            scanner.stopScan(callback)
        }
    }
}
