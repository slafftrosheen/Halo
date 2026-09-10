package com.halo.drivers.heycyan.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
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
 */
data class BleAdvertisement(
    val address: String,
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<String>,
    val manufacturerData: Map<String, String>
)

data class DiscoveredBleDevice(
    val advertisement: BleAdvertisement,
    val firstSeenAt: Long
)

class HeyCyanBleScanner(
    private val context: Context,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    private val scanner = adapter?.bluetoothLeScanner

    @SuppressLint("MissingPermission")
    fun scan(targetAddress: String? = null): Flow<BleAdvertisement> = callbackFlow {
        if (scanner == null) {
            close(IllegalStateException("Bluetooth is not enabled or supported"))
            return@callbackFlow
        }

        val discoveredAddresses = Collections.synchronizedSet(mutableSetOf<String>())
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = if (!targetAddress.isNullOrBlank()) {
            try {
                listOf(ScanFilter.Builder().setDeviceAddress(targetAddress).build())
            } catch (e: Exception) {
                close(IllegalArgumentException("Invalid BLE target address: $targetAddress", e))
                return@callbackFlow
            }
        } else {
            null
        }

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

        try {
            if (filters != null) {
                scanner.startScan(filters, scanSettings, callback)
            } else {
                scanner.startScan(callback)
            }
        } catch (e: SecurityException) {
            close(SecurityException("BLE scan permission is unavailable", e))
            return@callbackFlow
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {
                // Scanner cleanup is best-effort after flow cancellation/error.
            }
        }
    }
}
