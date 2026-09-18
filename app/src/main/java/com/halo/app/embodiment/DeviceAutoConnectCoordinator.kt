package com.halo.app.embodiment

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

        scope.launch(Dispatchers.IO) {
            bootstrapSavedDevices()
        }

        scope.launch(Dispatchers.IO) {
            bluetoothEnabled
                .drop(1)
                .filter { it }
                .collectLatest {
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
        val record = registry.getDevice(deviceId) ?: return false
        if (!record.autoConnect) return false

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
        val saved = registry.getSavedDevices()
        if (bluetoothEnabled.value &&
            saved.any { it.autoConnect && it.descriptor.kind == DeviceKind.HEYCYAN_GLASSES }
        ) {
            // Construction registers saved sessions and runs the host's initial retry.
            heyCyanHostProvider()
        }

        if (saved.any { it.autoConnect && it.descriptor.kind == DeviceKind.BUDDYBOT }) {
            retryBuddyBot()
        }
    }

    private suspend fun retryHeyCyan() {
        val hasAutoConnect = registry.getSavedDevices().any {
            it.autoConnect && it.descriptor.kind == DeviceKind.HEYCYAN_GLASSES
        }
        if (!hasAutoConnect) return
        heyCyanHostProvider().retryAutoConnect()
    }

    private suspend fun retryBuddyBot() {
        val host = buddyBotHostProvider()
        val record = registry.getDevice(host.deviceId) ?: return
        if (!record.autoConnect || record.descriptor.kind != DeviceKind.BUDDYBOT) return

        host.ensureDeviceRegistered()
        sessionManager.autoConnectDevice(host.deviceId)
    }
}
