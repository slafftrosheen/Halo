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
import java.util.concurrent.ConcurrentHashMap
import com.halo.drivers.heycyan.protocol.HeyCyanProtocolConstants
import com.halo.sdk.runtime.embodiment.DeviceKind

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

/**
 * Android BLE often delivers an incomplete first advertisement, followed by a
 * scan response containing the human-readable name or vendor service UUID.
 * Never discard that later evidence because an address was already observed.
 * RSSI-only updates are stored but do not cause UI recompositions.
 */
fun mergeBleAdvertisements(previous: BleAdvertisement?, incoming: BleAdvertisement): BleAdvertisement {
    if (previous == null || !previous.address.equals(incoming.address, ignoreCase = true)) return incoming
    return incoming.copy(
        name = incoming.name?.takeIf(String::isNotBlank) ?: previous.name,
        serviceUuids = (previous.serviceUuids + incoming.serviceUuids).distinct(),
        manufacturerData = previous.manufacturerData + incoming.manufacturerData,
    )
}

fun bleAdvertisementEvidenceChanged(previous: BleAdvertisement?, next: BleAdvertisement): Boolean =
    previous == null || previous.name != next.name ||
        previous.serviceUuids != next.serviceUuids || previous.manufacturerData != next.manufacturerData

/** An advertisement is a hint, not protocol or READY proof. */
fun heyCyanCandidateLabel(advertisement: BleAdvertisement): String {
    val name = advertisement.name.orEmpty().trim()
    val normalized = name.lowercase(java.util.Locale.ROOT)
    val colmiA2 = Regex("(^|[^a-z0-9])colmi[^a-z0-9]*a2($|[^a-z0-9])").containsMatchIn(normalized)
    return when {
        advertisement.serviceUuids.any {
            it.equals(HeyCyanProtocolConstants.PRIMARY_SERVICE.toString(), ignoreCase = true)
        } -> "HeyCyan service advertised · verify before use"
        colmiA2 -> "COLMI A2 · HeyCyan compatibility candidate"
        normalized.contains("heycyan") || normalized.startsWith("o_") ||
            normalized.startsWith("q_") || normalized.contains("bw-ag1") ->
            "HeyCyan compatibility candidate"
        else -> "Unverified device · HeyCyan protocol check required"
    }
}


/**
 * Family detection is only a hint from BLE advertisement evidence. Never use
 * it to assert a successful pairing or to bypass the vendor session handshake.
 * Known non-HeyCyan families must not be passed to the HeyCyan command driver.
 */
fun inferGlassesFamilyFromAdvertisement(advertisement: BleAdvertisement): DeviceKind {
    val uuids = advertisement.serviceUuids.map { it.lowercase(java.util.Locale.ROOT) }
    fun service(short: String): Boolean = uuids.any {
        it == "0000${short}-0000-1000-8000-00805f9b34fb"
    }
    if (service("aa12")) return DeviceKind.EYEVUE_GLASSES
    if (service("fdb3")) return DeviceKind.TUNEBUDS_GLASSES
    if (service("0bd3") || service("0bd1")) return DeviceKind.MYVU_GLASSES
    if (service("fea8")) return DeviceKind.MOYOUNG_W620_GLASSES
    if (uuids.any { it == HeyCyanProtocolConstants.PRIMARY_SERVICE.toString() }) {
        return DeviceKind.HEYCYAN_GLASSES
    }
    val name = advertisement.name.orEmpty().lowercase(java.util.Locale.ROOT)
    return when {
        Regex("(^|[^a-z0-9])colmi[^a-z0-9]*a2($|[^a-z0-9])").containsMatchIn(name) ||
            name.contains("heycyan") || name.startsWith("o_") ||
            name.startsWith("q_") || name.contains("bw-ag1") ->
            DeviceKind.HEYCYAN_GLASSES
        name.contains("eyevue") -> DeviceKind.EYEVUE_GLASSES
        name.contains("tunebuds") || name.contains("ab mate") || name.contains("xk one") ->
            DeviceKind.TUNEBUDS_GLASSES
        name.contains("myvu") || name.contains("star air") -> DeviceKind.MYVU_GLASSES
        name.contains("moyoung") || name.contains("w620") -> DeviceKind.MOYOUNG_W620_GLASSES
        name.contains("ray-ban meta") || name.contains("meta ray-ban") ->
            DeviceKind.META_RAYBAN_GLASSES
        name.contains("mentra") -> DeviceKind.MENTRA_LIVE_GLASSES
        name.contains("memomind") -> DeviceKind.MEMOMIND_GLASSES
        else -> DeviceKind.UNKNOWN
    }
}

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

        val advertisementsByAddress = ConcurrentHashMap<String, BleAdvertisement>()
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
                run {
                    val record = scanResult.scanRecord
                    val manufacturerData = buildMap {
                        record?.manufacturerSpecificData?.let { data ->
                            for (index in 0 until data.size()) {
                                put(data.keyAt(index).toString(), HexCodec.encode(data.valueAt(index)))
                            }
                        }
                    }
                    // Some devices put their name in scan response or Bluetooth cache,
                    // not the first advertisement. CONNECT permission may be absent.
                    val cachedName = try {
                        scanResult.device?.name
                    } catch (_: SecurityException) {
                        null
                    }
                    val incoming = BleAdvertisement(
                        address = address,
                        name = record?.deviceName?.takeIf(String::isNotBlank) ?: cachedName,
                        rssi = scanResult.rssi,
                        serviceUuids = record?.serviceUuids?.map { it.uuid.toString() } ?: emptyList(),
                        manufacturerData = manufacturerData
                    )
                    var changed: BleAdvertisement? = null
                    advertisementsByAddress.compute(address) { _, previous ->
                        val merged = mergeBleAdvertisements(previous, incoming)
                        if (bleAdvertisementEvidenceChanged(previous, merged)) changed = merged
                        merged
                    }
                    changed?.let { trySend(it) }
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
