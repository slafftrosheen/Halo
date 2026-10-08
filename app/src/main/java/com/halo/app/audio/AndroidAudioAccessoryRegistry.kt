package com.halo.app.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.halo.host.android.audio.AndroidAudioDeviceClassifier
import com.halo.runtime.cognition.continuity.EndpointPersistence
import com.halo.sdk.runtime.AudioDeviceKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID

/** Raw addresses belong exclusively to this platform routing owner. */
data class AndroidAudioAccessory internal constructor(
    val id: String, val label: String, val kind: AudioDeviceKind,
    val inputDeviceId: Int?, val outputDeviceId: Int?, val communicationDeviceId: Int?,
    internal val exactAddress: String?, val persistence: EndpointPersistence,
) {
    val canInput get() = inputDeviceId != null
    val canOutput get() = outputDeviceId != null
    val communicationCapable get() = communicationDeviceId != null
    override fun toString() = "AudioAccessory(kind=$kind,input=$canInput,output=$canOutput)"
}

class AndroidAudioAccessoryRegistry(context: Context) : AutoCloseable {
    private val manager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val session = UUID.randomUUID().toString()
    private val state = MutableStateFlow<List<AndroidAudioAccessory>>(emptyList())
    val accessories = state.asStateFlow()
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refresh()
    }
    init { manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper())); refresh() }
    @Synchronized fun refresh() {
        val inputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val communication = if (Build.VERSION.SDK_INT >= 31) manager.availableCommunicationDevices else emptyList()
        val devices = (inputs + outputs + communication).distinctBy { it.id }
            .filter { AndroidAudioDeviceClassifier.isAccessory(it.type) }.map { device ->
                AudioAccessoryEvidence(device.id, device.type, device.productName.toString(),
                    if (Build.VERSION.SDK_INT >= 28) device.address.trim().takeIf { it.isNotEmpty() } else null,
                    inputs.any { it.id == device.id }, outputs.any { it.id == device.id }, communication.any { it.id == device.id })
            }
        state.value = projectAudioAccessories(devices, session)
    }
    fun find(id: String?) = accessories.value.firstOrNull { it.id == id }
    fun findInput(id: Int) = accessories.value.firstOrNull { it.inputDeviceId == id }
    fun output(id: String?, platformId: Int?): AudioDeviceInfo? {
        refresh()
        val accessory = find(id)
        if (id != null && accessory == null) return null
        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val communicationOutput = accessory?.communicationDeviceId?.takeIf { communicationId ->
            manager.mode == AudioManager.MODE_IN_COMMUNICATION && outputs.any { it.id == communicationId }
        }
        val exactId = communicationOutput ?: accessory?.outputDeviceId ?: platformId ?: return null
        return outputs.firstOrNull { it.id == exactId &&
            AndroidAudioDeviceClassifier.isAccessory(it.type) &&
            (accessory?.exactAddress == null || (Build.VERSION.SDK_INT >= 28 && it.address.equals(accessory.exactAddress, true))) }
    }
    override fun close() = manager.unregisterAudioDeviceCallback(callback)
}

internal data class AudioAccessoryEvidence(val id: Int, val type: Int, val label: String, val address: String?,
    val input: Boolean, val output: Boolean, val communication: Boolean)

/** Address + transport family or current-session platform identity. Labels never merge endpoints. */
internal fun projectAudioAccessories(devices: List<AudioAccessoryEvidence>, session: String): List<AndroidAudioAccessory> {
    fun durable(device: AudioAccessoryEvidence): Boolean = device.type in setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET) &&
        device.address?.matches(Regex("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")) == true
    fun key(device: AudioAccessoryEvidence) = device.address?.takeIf { it.isNotBlank() }?.let {
        val exact = "${AndroidAudioDeviceClassifier.kind(device.type).name}:${it.lowercase(java.util.Locale.ROOT)}"
        if (durable(device)) exact else "$session:$exact"
    } ?: "$session:${device.id}"
    return devices.groupBy(::key).map { (identity, group) ->
        val input = group.firstOrNull { it.input }
        val output = group.filter { it.output }.sortedBy { if (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) 0 else 1 }.firstOrNull()
        val communication = group.firstOrNull { it.communication }
        val exact = group.first().address?.takeIf { it.isNotBlank() }
        AndroidAudioAccessory("platform-audio:" + MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }, group.first().label.ifBlank { "Audio accessory" },
            AndroidAudioDeviceClassifier.kind(group.first().type), input?.id, output?.id, communication?.id, exact,
            if (group.all(::durable)) EndpointPersistence.DURABLE else EndpointPersistence.SESSION_ONLY)
    }
}
