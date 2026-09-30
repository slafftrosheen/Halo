package com.halo.app.automation

import android.content.*
import android.net.*
import android.os.BatteryManager
import android.os.Build
import com.halo.runtime.cognition.routine.*
import com.halo.runtime.cognition.device.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.util.UUID

class AndroidBatterySignalSource(private val context: Context) : AutomationSignalSource {
    override val availability = MutableStateFlow(AutomationSourceStatus.AVAILABLE)
    override val signals = callbackFlow {
        var previousPercent: Int? = null
        var previousCharging: Boolean? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = if (status < 0) null else status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val prior = previousPercent; val wasCharging = previousCharging
                previousPercent = percent; previousCharging = charging
                if (prior != null && percent != null && prior != percent) trySend(signal(AutomationSignalKind.BATTERY_CHANGED, "battery",
                    mapOf("previous" to AutomationValue.Integer(prior.toLong()), "percent" to AutomationValue.Integer(percent.toLong()))))
                if (wasCharging != null && charging != null && wasCharging != charging) trySend(signal(
                    if(charging) AutomationSignalKind.CHARGING_STARTED else AutomationSignalKind.CHARGING_STOPPED, "charging"))
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }.catch { availability.value = AutomationSourceStatus.UNAVAILABLE }
}
class AndroidNetworkSignalSource(private val context: Context) : AutomationSignalSource {
    override val availability = MutableStateFlow(AutomationSourceStatus.AVAILABLE)
    override val signals = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        var previous: Boolean? = null
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun update() {
                val now = manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                val prior = previous; previous = now
                if (prior != null && now != prior) trySend(signal(if(now) AutomationSignalKind.NETWORK_AVAILABLE else AutomationSignalKind.NETWORK_LOST, "network"))
            }
            override fun onAvailable(network: Network) = update()
            override fun onLost(network: Network) = update()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update()
        }
        // Establish baseline before callback registration, including offline startup.
        previous = manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.catch { availability.value = AutomationSourceStatus.UNAVAILABLE }
}
class DeviceAutomationSignalSource(private val presence: DevicePresenceRepository) : AutomationSignalSource {
    override val availability = MutableStateFlow(AutomationSourceStatus.RUNTIME_ONLY)
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override val signals = flow {
        var previous: Map<String, DevicePresence>? = null
        presence.presenceMap.debounce(1500).collect { current ->
            val prior = previous; previous = current.toMap()
            if (prior != null) current.forEach { (id, now) ->
                val before = prior[id] ?: return@forEach
                fun DevicePresenceState.connected() = this == DevicePresenceState.CONNECTED || this == DevicePresenceState.READY
                if(before.state.connected() != now.state.connected()) emit(signal(
                    if(now.state.connected()) AutomationSignalKind.DEVICE_CONNECTED else AutomationSignalKind.DEVICE_DISCONNECTED,
                    "device", mapOf("device" to AutomationValue.Text(id)), AutomationTrust.TRUSTED_RUNTIME))
            }
        }
    }
}
private fun signal(kind: AutomationSignalKind, source: String, attributes: Map<String, AutomationValue> = emptyMap(),
    trust: AutomationTrust = AutomationTrust.PLATFORM_EVENT) = AutomationSignal(UUID.randomUUID().toString(), kind, Instant.now(), source, trust, attributes)
