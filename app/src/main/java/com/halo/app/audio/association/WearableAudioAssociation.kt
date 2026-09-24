package com.halo.app.audio.association

import com.halo.sdk.runtime.HaloDispatchers
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.productionHaloDispatchers
import kotlinx.coroutines.withContext

data class WearableAudioAssociation(
    val wearableDeviceId: String,
    val associationId: Int?,
    val bluetoothAddress: String?,
    val deviceName: String?,
    val bonded: Boolean,
    val lastSeenAudioType: Int?
)

class WearableAudioAssociationRepository(
    private val storage: Storage,
    private val dispatchers: HaloDispatchers = productionHaloDispatchers()
) {
    suspend fun read(wearableDeviceId: String): WearableAudioAssociation? = withContext(dispatchers.io) {
        readAt(wearableDeviceId, keyPrefix(wearableDeviceId))?.let { return@withContext it }
        readAt(wearableDeviceId, legacyKeyPrefix(wearableDeviceId))?.also { migrated ->
            writeAt(migrated, keyPrefix(wearableDeviceId))
        }
    }

    suspend fun write(value: WearableAudioAssociation) = withContext(dispatchers.io) {
        writeAt(value, keyPrefix(value.wearableDeviceId))
    }

    private fun readAt(wearableDeviceId: String, prefix: String): WearableAudioAssociation? {
        val address = storage.getString(prefix + "address")
        val associationId = storage.getInt(prefix + "association_id", NO_ASSOCIATION).takeUnless { it == NO_ASSOCIATION }
        if (address == null && associationId == null) return null
        return WearableAudioAssociation(
            wearableDeviceId = wearableDeviceId,
            associationId = associationId,
            bluetoothAddress = address,
            deviceName = storage.getString(prefix + "name"),
            bonded = storage.getBoolean(prefix + "bonded", false),
            lastSeenAudioType = storage.getInt(prefix + "audio_type", NO_AUDIO_TYPE).takeUnless { it == NO_AUDIO_TYPE }
        )
    }

    private fun writeAt(value: WearableAudioAssociation, prefix: String) {
        storage.putInt(prefix + "association_id", value.associationId ?: NO_ASSOCIATION)
        storage.putString(prefix + "address", value.bluetoothAddress)
        storage.putString(prefix + "name", value.deviceName)
        storage.putBoolean(prefix + "bonded", value.bonded)
        storage.putInt(prefix + "audio_type", value.lastSeenAudioType ?: NO_AUDIO_TYPE)
    }

    private fun keyPrefix(id: String) = "wearable_audio_v2_${id.hashCode()}_"
    private fun legacyKeyPrefix(id: String) = "heycyan_audio_${id.hashCode()}_"

    private companion object {
        const val NO_ASSOCIATION = -1
        const val NO_AUDIO_TYPE = -1
    }
}
