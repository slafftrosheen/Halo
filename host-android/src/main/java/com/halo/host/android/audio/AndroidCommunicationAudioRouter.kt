package com.halo.host.android.audio

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean

data class AudioDeviceSelector(
    val exactAddress: String? = null,
    val deviceId: Int? = null,
    val allowedTypes: Set<Int> = emptySet()
) {
    fun matches(device: AudioDeviceInfo): Boolean {
        if (deviceId != null && device.id != deviceId) return false
        if (allowedTypes.isNotEmpty() && device.type !in allowedTypes) return false
        if (exactAddress != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            if (!exactAddress.equals(device.address, ignoreCase = true)) return false
        }
        return deviceId != null || exactAddress != null
    }
}

interface CommunicationAudioRouter {
    suspend fun acquire(
        requestedDevice: AudioDeviceSelector,
        needsInput: Boolean,
        needsOutput: Boolean
    ): Result<CommunicationAudioLease>
}

class CommunicationAudioLease internal constructor(
    val selectedEndpoint: BluetoothAudioEndpoint,
    val previousMode: Int,
    val previousCommunicationDeviceId: Int?,
    val legacyScoStarted: Boolean,
    private val releaseAction: () -> Unit
) {
    private val released = AtomicBoolean(false)
    fun release() {
        if (released.compareAndSet(false, true)) releaseAction()
    }
}

class AndroidCommunicationAudioRouter(
    private val audioManager: AudioManager
) : CommunicationAudioRouter {
    private val lock = Any()
    private var activeLease: CommunicationAudioLease? = null

    override suspend fun acquire(
        requestedDevice: AudioDeviceSelector,
        needsInput: Boolean,
        needsOutput: Boolean
    ): Result<CommunicationAudioLease> = runCatching {
        require(needsInput || needsOutput) { "At least one audio direction is required" }
        synchronized(lock) {
            check(activeLease == null) { "A communication audio route is already active" }
            val candidates = communicationCandidates(needsInput, needsOutput)
            val selected = candidates.singleOrNull(requestedDevice::matches)
                ?: error("No unique, exactly associated communication endpoint is available")
            val previousMode = audioManager.mode
            val previousDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.communicationDevice
            } else null
            var scoStarted = false
            try {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    check(audioManager.setCommunicationDevice(selected)) { "Android rejected the communication endpoint" }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    scoStarted = true
                }
                val endpoint = selected.toBluetoothEndpoint(
                    direction = when {
                        needsInput && needsOutput -> AudioEndpointDirection.DUPLEX
                        needsInput -> AudioEndpointDirection.INPUT
                        else -> AudioEndpointDirection.OUTPUT
                    },
                    communicationCapable = true
                ) ?: error("Selected endpoint is not a supported Bluetooth audio device")
                CommunicationAudioLease(
                    endpoint,
                    previousMode,
                    previousDevice?.id,
                    scoStarted
                ) {
                    synchronized(lock) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            audioManager.clearCommunicationDevice()
                            if (previousDevice != null) audioManager.setCommunicationDevice(previousDevice)
                        } else if (scoStarted) {
                            @Suppress("DEPRECATION")
                            audioManager.stopBluetoothSco()
                        }
                        audioManager.mode = previousMode
                        activeLease = null
                    }
                }.also { activeLease = it }
            } catch (error: Throwable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.clearCommunicationDevice()
                else if (scoStarted) {
                    @Suppress("DEPRECATION")
                    audioManager.stopBluetoothSco()
                }
                audioManager.mode = previousMode
                throw error
            }
        }
    }

    private fun communicationCandidates(needsInput: Boolean, needsOutput: Boolean): List<AudioDeviceInfo> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return audioManager.availableCommunicationDevices
        val flag = when {
            needsInput && needsOutput -> AudioManager.GET_DEVICES_ALL
            needsInput -> AudioManager.GET_DEVICES_INPUTS
            else -> AudioManager.GET_DEVICES_OUTPUTS
        }
        return audioManager.getDevices(flag).filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    }
}
