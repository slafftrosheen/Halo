package com.halo.host.android.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioAttributes
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withTimeoutOrNull

enum class BluetoothAudioEndpointType {
    A2DP,
    SCO,
    BLE_HEADSET,
    BLE_SPEAKER
}

enum class RouteEvidence {
    PERSISTED_EXACT_ADDRESS,
    COMPANION_ASSOCIATION,
    NEW_ENDPOINT_AFTER_ROUTE_COMMAND,
    NAME_HEURISTIC,
    UNKNOWN
}

data class BluetoothAudioEndpoint(
    val address: String?,
    val name: String?,
    val type: BluetoothAudioEndpointType,
    val connected: Boolean,
    val routeEvidence: RouteEvidence = RouteEvidence.UNKNOWN
)

interface BluetoothAudioRouteMonitor {
    val routes: StateFlow<List<BluetoothAudioEndpoint>>

    suspend fun awaitEndpoint(
        predicate: (BluetoothAudioEndpoint) -> Boolean,
        timeoutMs: Long
    ): BluetoothAudioEndpoint?
}

class AndroidBluetoothAudioRouteMonitor(
    private val context: Context,
    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
) : BluetoothAudioRouteMonitor {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _routes = MutableStateFlow<List<BluetoothAudioEndpoint>>(emptyList())
    override val routes: StateFlow<List<BluetoothAudioEndpoint>> = _routes.asStateFlow()

    init {
        refreshEndpoints()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    refreshEndpoints()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    refreshEndpoints()
                }
            }, null)
        }
    }

    fun refreshEndpoints() {
        val list = mutableListOf<BluetoothAudioEndpoint>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Enumerate selectable endpoints. getAudioDevicesForAttributes only reports
            // the current policy route and can hide connected glasses before selection.
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in outputs) {
                val endpointType = when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> BluetoothAudioEndpointType.A2DP
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> BluetoothAudioEndpointType.SCO
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                            BluetoothAudioEndpointType.BLE_HEADSET
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                            BluetoothAudioEndpointType.BLE_SPEAKER
                        } else null
                    }
                }
                if (endpointType != null) {
                    val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) device.address else null
                    val name = device.productName?.toString()
                    list.add(
                        BluetoothAudioEndpoint(
                            address = address,
                            name = name,
                            type = endpointType,
                            connected = true
                        )
                    )
                }
            }
        }
        _routes.value = list
        Log.i("HaloAudio", "HALO_AUDIO_ROUTE_ANDROID candidates=${list.joinToString { "${it.type}:${it.name}" }}")
    }

    override suspend fun awaitEndpoint(
        predicate: (BluetoothAudioEndpoint) -> Boolean,
        timeoutMs: Long
    ): BluetoothAudioEndpoint? {
        refreshEndpoints()
        val current = _routes.value.firstOrNull(predicate)
        if (current != null) return current

        return withTimeoutOrNull(timeoutMs) {
            _routes.first { endpoints ->
                endpoints.any(predicate)
            }.first(predicate)
        }
    }
}
