package com.halo.app.ink

import com.halo.app.data.notebook.NotebookCanvasRepository
import com.halo.sdk.runtime.embodiment.DeviceSessionManager
import com.halo.sdk.runtime.ink.*
import com.halo.host.ink.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared UI/assistant domain owner. Pairing remains a protected, explicit local security act. */
class CrossDeviceInkController(
    private val devices: DeviceSessionManager,
    private val canvases: NotebookCanvasRepository,
    private val localOwner: String,
    private val activation: InkActivationPolicy = InkActivationPolicy.QUALIFICATION_PENDING,
) {
    private val mutex = Mutex()
    private val receivers = mutableMapOf<String, InkReceiverHost>()
    private val receiverJobs = mutableMapOf<String, Job>()
    private val approvedRevisions = mutableMapOf<String, String>()
    private val mutableConnections = MutableStateFlow<Map<String, InkReceiverState>>(emptyMap())
    val connections = mutableConnections.asStateFlow()
    val sessions get() = devices.ink.sessions
    val previews get() = devices.ink.previews
    val enabled get() = activation.enabled()

    /** Local security confirmation only; never exposed as a callable assistant operation. */
    suspend fun associateConfirmed(bundle: InkPairingBundle): Boolean = mutex.withLock {
        if (!activation.enabled()) return@withLock false
        devices.ink.associate(com.halo.sdk.runtime.DeviceIdentity(bundle.endpoint.deviceId, "InkSurface", bundle.displayName),
            bundle.association())
    }

    suspend fun start(deviceId: String, boardId: String, documentId: String,
        expectedRevision: String, interactionId: String): InkSession? = mutex.withLock {
        if (!activation.enabled()) return@withLock null
        if (canvases.remoteInkRevision(boardId, documentId) != expectedRevision) return@withLock null
        devices.ink.sessions.value.filter { it.inputOwner == deviceId }.forEach { stopOwned(it.sessionId) }
        devices.ink.start(deviceId, localOwner, localOwner, interactionId, boardId, documentId)?.also {
            approvedRevisions[it.sessionId] = expectedRevision
        }
    }
    suspend fun stop(sessionId: String): Boolean = mutex.withLock { stopOwned(sessionId) }
    suspend fun stopAll() = mutex.withLock {
        (devices.ink.sessions.value.map { it.sessionId } + receivers.keys).distinct().forEach { stopOwned(it) }
    }
    suspend fun disconnectHosts() = mutex.withLock {
        // Keep an unconnected, bounded consent while the person chooses a local connection
        // file in Android's document picker. Active input channels always stop on background.
        receivers.keys.toList().forEach { stopOwned(it) }
    }
    suspend fun forget(deviceId: String) = mutex.withLock {
        devices.ink.sessions.value.filter { it.inputOwner == deviceId }.forEach { stopOwned(it.sessionId) }
        devices.ink.forget(deviceId)
    }
    suspend fun devices() = devices.registry.getSavedDevices().filter { it.inkAssociation != null }.take(64)
    suspend fun revision(boardId: String, documentId: String) = canvases.remoteInkRevision(boardId, documentId)
    suspend fun selection(sessionId: String, interactionId: String) = devices.ink.selection(sessionId, interactionId)

    /** Explicit host address selection and protected identity come from the local security UI. */
    suspend fun listen(sessionId: String, context: javax.net.ssl.SSLContext, address: java.net.InetAddress,
        port: Int, scope: CoroutineScope): Int? = mutex.withLock {
        if (!activation.enabled()) return@withLock null
        receivers[sessionId]?.let { return@withLock it.localPort }
        val session = devices.ink.sessions.value.firstOrNull { it.sessionId == sessionId } ?: return@withLock null
        val endpoint = devices.registry.getDevice(session.inputOwner)?.inkAssociation?.endpoint ?: return@withLock null
        val approvedRevision = approvedRevisions[sessionId] ?: return@withLock null
        val receiver = withContext(Dispatchers.IO) { InkReceiverHost(context, address, port, devices.ink, sessionId, offer = { current ->
            val snapshot = canvases.remoteInkSnapshot(current.boardId, current.documentId)
            snapshot?.takeIf { it.first == approvedRevision }?.let {
                InkSessionOffer(current, endpoint, it.first, it.second)
            }
        }, scope, activation) }
        receivers[sessionId] = receiver
        receiverJobs[sessionId] = scope.launch {
            receiver.state.first { state ->
                mutex.withLock {
                    mutableConnections.value = mutableConnections.value + (sessionId to state)
                    if (state == InkReceiverState.DISCONNECTED) {
                        receivers.remove(sessionId)
                        receiverJobs.remove(sessionId)
                        approvedRevisions.remove(sessionId)
                        // Bound terminal observations to the most recent four attempts.
                        mutableConnections.value = mutableConnections.value.entries.toList().takeLast(4).associate { it.toPair() }
                    }
                }
                state == InkReceiverState.DISCONNECTED
            }
        }
        receiver.localPort
    }

    private suspend fun stopOwned(sessionId: String): Boolean {
        receivers.remove(sessionId)?.close()
        receiverJobs.remove(sessionId)?.cancel()
        approvedRevisions.remove(sessionId)
        mutableConnections.value = mutableConnections.value - sessionId
        return devices.ink.stop(sessionId)
    }
}
