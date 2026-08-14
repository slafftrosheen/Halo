package com.halo.sdk.runtime.embodiment

import com.halo.sdk.runtime.DeviceIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages active embodiment sessions, ensuring devices are connected
 * when needed and routing intents to the correct active session.
 */
class DeviceSessionManager(
    private val registry: DeviceRegistry
) {
    private val _sessions = mutableMapOf<String, EmbodimentSession>()
    
    // Simplified state flow of active sessions for UI/Observer use
    private val _activeSessionsFlow = MutableStateFlow<List<EmbodimentSession>>(emptyList())
    val activeSessions: StateFlow<List<EmbodimentSession>> = _activeSessionsFlow.asStateFlow()

    /**
     * Add a session to the manager.
     */
    fun registerSession(session: EmbodimentSession) {
        _sessions[session.identity.deviceId] = session
        updateFlow()
    }

    /**
     * Remove a session.
     */
    fun unregisterSession(deviceId: String) {
        _sessions.remove(deviceId)
        updateFlow()
    }

    /**
     * Get the active session for a specific device identity.
     */
    fun getSession(deviceId: String): EmbodimentSession? {
        return _sessions[deviceId]
    }

    /**
     * Try to auto-connect to any saved devices marked as autoConnect.
     */
    suspend fun autoConnectSavedDevices() {
        val saved = registry.getSavedDevices()
        for (record in saved) {
            if (record.autoConnect) {
                // In a real implementation, we would instantiate the correct transport
                // based on the deviceType and try to connect.
                val session = _sessions[record.identity.deviceId]
                if (session != null && session.state.value == SessionState.DISCONNECTED) {
                    session.connect()
                }
            }
        }
    }
    
    private fun updateFlow() {
        _activeSessionsFlow.value = _sessions.values.toList()
    }
}
