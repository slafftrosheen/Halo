package com.halo.sdk.runtime.embodiment

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Manages active embodiment sessions, ensuring devices are connected
 * when needed and routing intents to the correct active session.
 */
class DeviceSessionManager(
    val registry: DeviceRegistry
) {
    private val _sessions = mutableMapOf<String, EmbodimentSession>()
    private val stateScope = CoroutineScope(Dispatchers.Default)
    private val stateJobs = mutableMapOf<String, Job>()

    private val _activeSessionsFlow = MutableStateFlow<List<EmbodimentSession>>(emptyList())
    val activeSessions: StateFlow<List<EmbodimentSession>> = _activeSessionsFlow.asStateFlow()

    /** Emits whenever a registered session changes state. */
    private val _stateRevision = MutableStateFlow(0L)
    val stateRevision: StateFlow<Long> = _stateRevision.asStateFlow()

    fun registerSession(session: EmbodimentSession) {
        stateJobs.remove(session.identity.deviceId)?.cancel()
        _sessions[session.identity.deviceId] = session
        stateJobs[session.identity.deviceId] = stateScope.launch {
            session.state.collect { _stateRevision.value += 1 }
        }
        updateFlow()
    }

    fun unregisterSession(deviceId: String) {
        stateJobs.remove(deviceId)?.cancel()
        _sessions.remove(deviceId)
        updateFlow()
        _stateRevision.value += 1
    }

    fun getSession(deviceId: String): EmbodimentSession? = _sessions[deviceId]

    suspend fun autoConnectDevice(deviceId: String): Boolean {
        val record = registry.getDevice(deviceId) ?: return false
        if (!record.autoConnect) return false
        val session = _sessions[deviceId] ?: return false
        if (session.state.value == SessionState.READY || session.state.value == SessionState.CONNECTED || session.state.value == SessionState.HANDSHAKE || session.state.value == SessionState.CAPABILITY_SYNC || session.state.value == SessionState.STATE_SYNC) {
            return true
        }
        for (attempt in 1..3) {
            try {
                session.connect()
                if (session.state.value != SessionState.DISCONNECTED && session.state.value != SessionState.SAVED && session.state.value != SessionState.ERROR) {
                    return true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Continue with bounded retry.
            }
            kotlinx.coroutines.delay(5000L * attempt)
        }
        return session.state.value == SessionState.READY
    }

    suspend fun autoConnectSavedDevices() {
        for (record in registry.getSavedDevices()) {
            autoConnectDevice(record.identity.deviceId)
        }
    }

    private fun updateFlow() {
        _activeSessionsFlow.value = _sessions.values.toList()
    }
}
