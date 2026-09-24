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

enum class AudioEndpointDirection { INPUT, OUTPUT, DUPLEX }

enum class RouteEvidence {
    PERSISTED_EXACT_ADDRESS,
    COMPANION_ASSOCIATION,
    NEW_ENDPOINT_AFTER_ROUTE_COMMAND,
    NAME_HEURISTIC,
    UNKNOWN
}

data class BluetoothAudioEndpoint(
    val deviceId: Int? = null,
    val address: String?,
    val name: String?,
    val type: BluetoothAudioEndpointType,
    val connected: Boolean,
    val direction: AudioEndpointDirection = AudioEndpointDirection.OUTPUT,
    val communicationCapable: Boolean = false,
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
        val endpointsById = linkedMapOf<Int, BluetoothAudioEndpoint>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addDevices(endpointsById, audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS), AudioEndpointDirection.OUTPUT)
            addDevices(endpointsById, audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS), AudioEndpointDirection.INPUT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                addDevices(endpointsById, audioManager.availableCommunicationDevices.toTypedArray(), null, true)
            }
        }
        val list = endpointsById.values.toList()
        _routes.value = list
        Log.i("HaloAudio", "HALO_AUDIO_ROUTE_ANDROID candidates=${list.joinToString { "${it.direction}:${it.type}:${it.name}" }}")
    }

    private fun addDevices(
        destination: MutableMap<Int, BluetoothAudioEndpoint>,
        devices: Array<out AudioDeviceInfo>,
        direction: AudioEndpointDirection?,
        communicationCapable: Boolean = false
    ) {
        for (device in devices) {
            val endpoint = device.toBluetoothEndpoint(direction ?: AudioEndpointDirection.DUPLEX, communicationCapable)
                ?: continue
            val previous = destination[device.id]
            destination[device.id] = if (previous == null) endpoint else previous.copy(
                direction = if (previous.direction == endpoint.direction) previous.direction else AudioEndpointDirection.DUPLEX,
                communicationCapable = previous.communicationCapable || endpoint.communicationCapable
            )
        }
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

internal fun AudioDeviceInfo.toBluetoothEndpoint(
    direction: AudioEndpointDirection,
    communicationCapable: Boolean
): BluetoothAudioEndpoint? {
    val endpointType = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> BluetoothAudioEndpointType.A2DP
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> BluetoothAudioEndpointType.SCO
        else -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET -> BluetoothAudioEndpointType.BLE_HEADSET
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_SPEAKER -> BluetoothAudioEndpointType.BLE_SPEAKER
            else -> null
        }
    } ?: return null
    return BluetoothAudioEndpoint(
        deviceId = id,
        address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) address else null,
        name = productName?.toString(),
        type = endpointType,
        connected = true,
        direction = direction,
        communicationCapable = communicationCapable
    )
}
