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
    val registry: DeviceRegistry
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
     * Try to auto-connect to any saved devices marked as autoConnect, with a simple retry mechanism.
     */
    suspend fun autoConnectSavedDevices() {
        val saved = registry.getSavedDevices()
        for (record in saved) {
            if (record.autoConnect) {
                val session = _sessions[record.identity.deviceId]
                if (session != null && session.state.value == SessionState.DISCONNECTED) {
                    // Try to connect up to 3 times with 5 second delays
                    for (attempt in 1..3) {
                        try {
                            session.connect()
                            // Assuming session.connect() doesn't block forever, check state
                            if (session.state.value != SessionState.DISCONNECTED) {
                                break // Connecting or Ready
                            }
                        } catch (e: Exception) {
                            // Suppress
                        }
                        kotlinx.coroutines.delay(5000L * attempt) // 5s, 10s, 15s backoff
                        
                        // Check if we managed to connect in the meantime
                        if (session.state.value != SessionState.DISCONNECTED) {
                            break
                        }
                    }
                }
            }
        }
    }
    
    private fun updateFlow() {
        _activeSessionsFlow.value = _sessions.values.toList()
    }
}
