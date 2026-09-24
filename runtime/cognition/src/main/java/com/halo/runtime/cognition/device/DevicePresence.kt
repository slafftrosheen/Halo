package com.halo.runtime.cognition.device

import com.halo.cognition.language.InteractionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

enum class DevicePresenceState {
    ASSOCIATED,
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
    val lastSeenAt: Instant? = null,
)

data class InteractionContinuity(
    val interactionId: InteractionId,
    val originDeviceId: String?,
    val currentInputRoute: String,
    val currentOutputRoute: String,
    val startedAt: Instant = Instant.now(),
    val allowHandoff: Boolean = false,
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
        mutablePresence.update { current ->
            val existing = current[presence.deviceId]
            if (existing != null && presence.updatedAt.isBefore(existing.updatedAt)) current
            else current + (presence.deviceId to presence.copy(lastSeenAt = presence.lastSeenAt ?: existing?.lastSeenAt))
        }
    }

    override fun getPresence(deviceId: String): DevicePresence? {
        return mutablePresence.value[deviceId]
    }
}
