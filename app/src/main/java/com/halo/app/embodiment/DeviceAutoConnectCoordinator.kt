package com.halo.app.embodiment

import android.util.Log
import com.halo.drivers.heycyan.HeyCyanHost
import com.halo.sdk.runtime.embodiment.DeviceKind
import com.halo.sdk.runtime.embodiment.DeviceRegistry
import com.halo.sdk.runtime.embodiment.DeviceSessionManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Process-level owner for saved-device auto-connect policy.
 *
 * The coordinator stays transport-agnostic at startup: it first inspects the
 * registry and only constructs a physical host when that device kind has an
 * explicit saved autoConnect=true record.
 */
internal fun normalizeLegacyDeviceRecord(
    record: com.halo.sdk.runtime.embodiment.DeviceRecord
): com.halo.sdk.runtime.embodiment.DeviceRecord {
    val identity = record.identity

    // Migration for the short-lived Pairing constructor-order bug where the
    // Bluetooth display name was written into deviceType and the canonical
    // type string was written into displayName.
    if (
        identity.displayName == "HEYCYAN_GLASSES" &&
        identity.deviceType != "HEYCYAN_GLASSES"
    ) {
        return record.copy(
            identity = identity.copy(
                deviceType = "HEYCYAN_GLASSES",
                displayName = identity.deviceType.ifBlank { "HeyCyan Glasses" }
            )
        )
    }

    return record
}

internal fun autoConnectDeviceIds(
    records: List<com.halo.sdk.runtime.embodiment.DeviceRecord>,
    kind: DeviceKind
): List<String> = records
    .filter { it.autoConnect && it.descriptor.kind == kind }
    .map { it.identity.deviceId }

class DeviceAutoConnectCoordinator(
    private val registry: DeviceRegistry,
    private val sessionManager: DeviceSessionManager,
    private val heyCyanHostProvider: () -> HeyCyanHost,
    private val buddyBotHostProvider: () -> BuddyBotHost,
    private val bluetoothEnabled: StateFlow<Boolean>,
    private val networkRevision: StateFlow<Long>,
    private val scope: CoroutineScope
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        Log.i(TAG, "AUTOCONNECT_COORDINATOR_START")

        scope.launch(Dispatchers.IO) {
            bootstrapSavedDevices()
        }

        scope.launch(Dispatchers.IO) {
            bluetoothEnabled
                .drop(1)
                .filter { it }
                .collectLatest {
                    Log.i(TAG, "AUTOCONNECT_TRIGGER kind=heycyan source=bluetooth_on")
                    retryHeyCyan()
                }
        }

        scope.launch(Dispatchers.IO) {
            networkRevision
                .drop(1)
                .collectLatest {
                    // Coalesce noisy capability callbacks into one reconnect probe.
                    delay(750L)
                    retryBuddyBot()
                }
        }
    }

    suspend fun retryDevice(deviceId: String): Boolean {
        val original = registry.getDevice(deviceId) ?: return false
        val record = normalizeLegacyDeviceRecord(original)
        if (record != original) {
            registry.saveDevice(record)
        }
        if (!record.autoConnect) return false

        Log.i(TAG, "AUTOCONNECT_TRIGGER kind=${record.descriptor.kind.name.lowercase()} source=user_toggle")
        return when (record.descriptor.kind) {
            DeviceKind.HEYCYAN_GLASSES -> {
                if (!bluetoothEnabled.value) return false
                val host = heyCyanHostProvider()
                host.setTargetDevice(deviceId)
                host.retryAutoConnect()
            }
            DeviceKind.BUDDYBOT -> {
                val host = buddyBotHostProvider()
                if (host.deviceId != deviceId) return false
                host.ensureDeviceRegistered()
                sessionManager.autoConnectDevice(deviceId)
            }
            DeviceKind.UNKNOWN -> false
        }
    }

    private suspend fun bootstrapSavedDevices() {
        val rawSaved = registry.getSavedDevices()
        val saved = rawSaved.map(::normalizeLegacyDeviceRecord)

        saved.zip(rawSaved).forEach { (normalized, original) ->
            if (normalized != original) {
                registry.saveDevice(normalized)
            }
        }

        if (
            bluetoothEnabled.value &&
            autoConnectDeviceIds(saved, DeviceKind.HEYCYAN_GLASSES).isNotEmpty()
        ) {
            // Construction registers saved sessions and runs the host's initial retry.
            heyCyanHostProvider()
        }

        if (autoConnectDeviceIds(saved, DeviceKind.BUDDYBOT).isNotEmpty()) {
            retryBuddyBot()
        }
    }

    private suspend fun retryHeyCyan() {
        val rawSaved = registry.getSavedDevices()
        val saved = rawSaved.map(::normalizeLegacyDeviceRecord)
        saved.zip(rawSaved).forEach { (normalized, original) ->
            if (normalized != original) {
                registry.saveDevice(normalized)
            }
        }

        val hasAutoConnect = autoConnectDeviceIds(
            saved,
            DeviceKind.HEYCYAN_GLASSES
        ).isNotEmpty()
        if (!hasAutoConnect) return
        val success = heyCyanHostProvider().retryAutoConnect()
        Log.i(TAG, "AUTOCONNECT_RESULT kind=heycyan success=$success")
    }

    private suspend fun retryBuddyBot() {
        val targetIds = autoConnectDeviceIds(
            registry.getSavedDevices(),
            DeviceKind.BUDDYBOT
        )
        if (targetIds.isEmpty()) return

        val host = buddyBotHostProvider()
        if (host.deviceId !in targetIds) return

        host.ensureDeviceRegistered()
        val success = sessionManager.autoConnectDevice(host.deviceId)
        Log.i(TAG, "AUTOCONNECT_RESULT kind=buddybot success=$success")
    }

    private companion object {
        const val TAG = "HaloAutoConnect"
    }
}
