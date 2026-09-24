package com.halo.app.embodiment

import com.halo.runtime.cognition.device.*
import com.halo.sdk.runtime.embodiment.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.time.Instant

class DevicePresenceBridge(
    private val repository: DevicePresenceRepository,
    private val registry: DeviceRegistry,
    private val sessions: DeviceSessionManager,
    private val scope: CoroutineScope,
) {
    init {
        scope.launch {
            registry.getSavedDevices().forEach { record ->
                repository.updatePresence(DevicePresence(record.identity.deviceId, DevicePresenceState.ASSOCIATED,
                    lastSeenAt = record.lastSeenMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli)))
            }
            sessions.stateRevision.collect {
                registry.getSavedDevices().forEach { record ->
                    val session = sessions.getSession(record.identity.deviceId) ?: return@forEach
                    val state = when (session.state.value) {
                        SessionState.READY -> DevicePresenceState.READY
                        SessionState.CONNECTED, SessionState.HANDSHAKE, SessionState.CAPABILITY_SYNC, SessionState.STATE_SYNC -> DevicePresenceState.CONNECTED
                        SessionState.DISCONNECTED, SessionState.ERROR, SessionState.DISCONNECTING -> DevicePresenceState.ABSENT
                        SessionState.DISCOVERED, SessionState.CONNECTING -> DevicePresenceState.NEARBY
                        SessionState.SAVED -> DevicePresenceState.ASSOCIATED
                    }
                    val now = Instant.now()
                    val evidence = when (state) {
                        DevicePresenceState.READY -> setOf(PresenceEvidence.SESSION_READY)
                        DevicePresenceState.CONNECTED -> setOf(PresenceEvidence.SESSION_CONNECTED)
                        else -> emptySet()
                    }
                    repository.updatePresence(DevicePresence(record.identity.deviceId, state, evidence, now,
                        now.takeIf { state == DevicePresenceState.READY || state == DevicePresenceState.CONNECTED }))
                }
            }
        }
    }
    fun companionPresence(address: String, appeared: Boolean) {
        scope.launch {
            val record = registry.getSavedDevices().firstOrNull { it.identity.deviceId.equals(address, true) }
            val id = record?.identity?.deviceId ?: address
            val now = Instant.now()
            if (appeared && record != null) registry.saveDevice(record.copy(lastSeenMs = now.toEpochMilli()))
            val session = sessions.getSession(id)
            // Companion proximity never overrides the connected session's transport truth.
            if (session?.state?.value in setOf(SessionState.READY, SessionState.CONNECTED, SessionState.HANDSHAKE,
                    SessionState.CAPABILITY_SYNC, SessionState.STATE_SYNC)) return@launch
            repository.updatePresence(DevicePresence(id, if (appeared) DevicePresenceState.NEARBY else DevicePresenceState.ABSENT,
                setOf(PresenceEvidence.COMPANION_PRESENCE), now, now.takeIf { appeared }))
        }
    }
}
