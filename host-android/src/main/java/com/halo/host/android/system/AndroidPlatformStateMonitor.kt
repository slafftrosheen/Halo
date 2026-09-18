package com.halo.host.android.system

import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-level projection of Android platform state used by recovery flows.
 *
 * This class owns only observation. It never requests permissions, enables radios,
 * navigates UI, or retries product operations by itself.
 */
class AndroidPlatformStateMonitor(
    context: Context
) : AutoCloseable {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _bluetoothEnabled = MutableStateFlow(readBluetoothEnabled())
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    private val _networkRevision = MutableStateFlow(0L)
    val networkRevision: StateFlow<Long> = _networkRevision.asStateFlow()

    private var receiverRegistered = false
    private var networkCallbackRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED) {
                _bluetoothEnabled.value = readBluetoothEnabled()
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = bumpNetworkRevision()
        override fun onLost(network: Network) = bumpNetworkRevision()

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) = bumpNetworkRevision()
    }

    init {
        registerBluetoothReceiver()
        registerNetworkCallback()
    }

    private fun readBluetoothEnabled(): Boolean = try {
        bluetoothManager?.adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    private fun bumpNetworkRevision() {
        _networkRevision.value = _networkRevision.value + 1L
    }

    @Suppress("DEPRECATION")
    private fun registerBluetoothReceiver() {
        val filter = IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(
                    bluetoothReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                appContext.registerReceiver(bluetoothReceiver, filter)
            }
            receiverRegistered = true
        } catch (_: SecurityException) {
            // Permission recovery is surfaced by the app-level prerequisite model.
        }
    }

    private fun registerNetworkCallback() {
        val manager = connectivityManager ?: return
        try {
            manager.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        } catch (_: SecurityException) {
            // ACCESS_NETWORK_STATE is expected, but recovery must remain non-fatal.
        } catch (_: RuntimeException) {
            // Some OEM stacks can reject duplicate/early callbacks during boot.
        }
    }

    override fun close() {
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(bluetoothReceiver) }
            receiverRegistered = false
        }
        if (networkCallbackRegistered) {
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
    }
}
