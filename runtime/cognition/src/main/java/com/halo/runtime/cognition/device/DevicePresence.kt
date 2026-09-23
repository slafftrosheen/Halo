package com.halo.runtime.cognition.device

import com.halo.cognition.language.InteractionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

enum class DevicePresenceState {
    UNKNOWN,
    ABSENT,
    NEARBY,
    CONNECTED,
    READY,
}

enum class PresenceEvidence {
    COMPANION_PRESENCE,
    BLE_VISIBLE,
    BLUETOOTH_CONNECTED,
    SESSION_CONNECTED,
    SESSION_READY,
}

data class DevicePresence(
    val deviceId: String,
    val state: DevicePresenceState,
    val evidence: Set<PresenceEvidence> = emptySet(),
    val updatedAt: Instant = Instant.now(),
)

data class InteractionContinuity(
    val interactionId: InteractionId,
    val originDeviceId: String?,
    val currentInputRoute: String,
    val currentOutputRoute: String,
    val startedAt: Instant = Instant.now(),
)

interface DevicePresenceRepository {
    val presenceMap: StateFlow<Map<String, DevicePresence>>
    suspend fun updatePresence(presence: DevicePresence)
    fun getPresence(deviceId: String): DevicePresence?
}

class InMemoryDevicePresenceRepository : DevicePresenceRepository {
    private val mutablePresence = MutableStateFlow<Map<String, DevicePresence>>(emptyMap())
    override val presenceMap: StateFlow<Map<String, DevicePresence>> = mutablePresence.asStateFlow()

    override suspend fun updatePresence(presence: DevicePresence) {
        val current = mutablePresence.value.toMutableMap()
        val existing = current[presence.deviceId]
        // Prevent stale ABSENT from overwriting READY unless timestamp is newer
        if (existing != null && presence.state == DevicePresenceState.ABSENT && existing.state == DevicePresenceState.READY) {
            if (presence.updatedAt.isBefore(existing.updatedAt)) return
        }
        current[presence.deviceId] = presence
        mutablePresence.value = current
    }

    override fun getPresence(deviceId: String): DevicePresence? {
        return mutablePresence.value[deviceId]
    }
}
