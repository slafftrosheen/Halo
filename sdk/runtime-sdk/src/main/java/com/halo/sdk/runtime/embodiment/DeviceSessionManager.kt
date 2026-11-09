package com.halo.sdk.runtime.embodiment

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Owns active sessions; connection serialization is per physical endpoint. */
class DeviceSessionManager(
    val registry: DeviceRegistry,
    inkActivation: com.halo.sdk.runtime.ink.InkActivationPolicy = com.halo.sdk.runtime.ink.InkActivationPolicy.QUALIFICATION_PENDING,
    inkCheckpoints: com.halo.sdk.runtime.ink.InkCheckpointStore = com.halo.sdk.runtime.ink.InkCheckpointStore.UNAVAILABLE,
) {
    val ink = com.halo.sdk.runtime.ink.RemoteInkSessionOwner(registry, inkActivation, inkCheckpoints)
    private val sessions = ConcurrentHashMap<String, EmbodimentSession>()
    private val stateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val stateJobs = mutableMapOf<String, Job>()
    private val registrationLock = Any()
    private val connectionLocks = ConcurrentHashMap<String, Mutex>()
    private val activeSessionsFlow = MutableStateFlow<List<EmbodimentSession>>(emptyList())
    val activeSessions: StateFlow<List<EmbodimentSession>> = activeSessionsFlow.asStateFlow()
    private val mutableRevision = MutableStateFlow(0L)
    val stateRevision: StateFlow<Long> = mutableRevision.asStateFlow()

    fun registerSession(session: EmbodimentSession) = synchronized(registrationLock) {
        val id = session.identity.deviceId
        stateJobs.remove(id)?.cancel()
        sessions[id] = session
        stateJobs[id] = stateScope.launch {
            session.state.collect {
                if (sessions[id] === session) {
                    mutableRevision.update { revision -> revision + 1 }
                    updateFlow()
                }
            }
        }
        updateFlow()
    }

    fun unregisterSession(deviceId: String) = synchronized(registrationLock) {
        stateJobs.remove(deviceId)?.cancel()
        sessions.remove(deviceId)
        updateFlow()
        mutableRevision.update { it + 1 }
    }

    fun getSession(deviceId: String): EmbodimentSession? = sessions[deviceId]

    /** A handshake may precede persistence. Notify projections AFTER the record is durable. */
    fun refreshSavedDevices() { mutableRevision.update { it + 1 } }

    suspend fun autoConnectDevice(deviceId: String): Boolean =
        connectionLocks.getOrPut(deviceId) { Mutex() }.withLock {
            val session = sessions[deviceId] ?: return@withLock false
            if (registry.getDevice(deviceId)?.autoConnect != true) return@withLock false
            if (session.state.value == SessionState.READY) return@withLock true
            if (isConnectionInProgress(session.state.value)) return@withLock awaitReadyOrTerminal(session)
            for (attempt in 1..3) {
                // Forget/opt-out while backoff is pending cancels the retry, not just the UI.
                if (sessions[deviceId] !== session || registry.getDevice(deviceId)?.autoConnect != true) return@withLock false
                try {
                    session.connect()
                    if (awaitReadyOrTerminal(session)) return@withLock true
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Bounded retry of the same saved endpoint. */ }
                if (attempt < 3) delay(5_000L * attempt)
            }
            sessions[deviceId] === session && session.state.value == SessionState.READY
        }

    suspend fun autoConnectSavedDevices() {
        for (record in registry.getSavedDevices()) autoConnectDevice(record.identity.deviceId)
    }

    private suspend fun awaitReadyOrTerminal(session: EmbodimentSession, timeoutMillis: Long = 30_000L): Boolean {
        if (session.state.value == SessionState.READY) return true
        val resolved = withTimeoutOrNull(timeoutMillis) {
            session.state.filter { it == SessionState.READY || it == SessionState.ERROR || it == SessionState.DISCONNECTED }.first()
        }
        return resolved == SessionState.READY
    }

    private fun isConnectionInProgress(state: SessionState): Boolean = state in setOf(
        SessionState.CONNECTING, SessionState.CONNECTED, SessionState.HANDSHAKE,
        SessionState.CAPABILITY_SYNC, SessionState.STATE_SYNC,
    )

    private fun updateFlow() { activeSessionsFlow.value = sessions.values.toList() }
}
