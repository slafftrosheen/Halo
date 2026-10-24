package com.halo.sdk.runtime.ink

import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.embodiment.DeviceRecord
import com.halo.sdk.runtime.embodiment.DeviceRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID

/** Release wiring uses QUALIFICATION_PENDING. Developer tools do not override this policy. */
fun interface InkActivationPolicy {
    fun enabled(): Boolean
    companion object { val QUALIFICATION_PENDING = InkActivationPolicy { false } }
}

/** Public certificate digest only. Private keys belong to a host's protected key store. */
data class InkAssociation(val endpoint: InkEndpoint, val certificateSha256: String) {
    init { require(certificateSha256.matches(Regex("[a-f0-9]{64}"))) }
}

data class InkSession(
    val sessionId: String,
    val inputOwner: String,
    val displayOwner: String,
    val processingOwner: String,
    val interactionId: String,
    val boardId: String,
    val documentId: String,
    val startedAtMs: Long,
    val expiresAtMs: Long,
)

enum class InkReceiveStatus {
    SAVED, DUPLICATE, PREVIEW, STOPPED, DISABLED, UNTRUSTED, STALE, CONFLICT, RATE_LIMITED,
    MALFORMED, STORAGE_UNAVAILABLE,
}

/** A receipt is content-free; SAVED is returned only after the canonical transaction commits. */
data class InkReceiveReceipt(val status: InkReceiveStatus, val revision: String? = null)

data class InkCheckpointRequest(
    val session: InkSession,
    val sequence: Long,
    val expectedRevision: String,
    val payloadSha256: String,
    val document: InkDocument,
) {
    init {
        require(sequence in 1 until Long.MAX_VALUE)
        requireInkReference(expectedRevision)
        require(payloadSha256.matches(Regex("[a-f0-9]{64}")))
        InkLimits.requireValid(document)
    }
}

/**
 * Host contract: atomically check board/document ownership + expected revision, store canonical
 * ink and last (session, sequence, digest, resulting revision) receipt in ONE transaction.
 * Same retry returns DUPLICATE even if expectedRevision predates its own commit. Different
 * content for the same sequence is CONFLICT. No tool execution or new board creation here.
 */
fun interface InkCheckpointStore {
    suspend fun commit(request: InkCheckpointRequest): InkReceiveReceipt
    suspend fun selectionIsCurrent(session: InkSession, expectedRevision: String, selection: InkSelectionContext): Boolean = false
    companion object {
        val UNAVAILABLE = InkCheckpointStore { InkReceiveReceipt(InkReceiveStatus.STORAGE_UNAVAILABLE) }
    }
}

/** Canonical device-owned, explicitly started input sessions. Not an EmbodimentIntent dispatcher. */
class RemoteInkSessionOwner(
    private val registry: DeviceRegistry,
    private val activation: InkActivationPolicy = InkActivationPolicy.QUALIFICATION_PENDING,
    private val checkpoints: InkCheckpointStore = InkCheckpointStore.UNAVAILABLE,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private data class Active(
        val session: InkSession,
        val certificateSha256: String,
        var previewSequence: Long = 0,
        var checkpointSequence: Long = 0,
        var windowStart: Long,
        var previewCount: Int = 0,
        var checkpointCount: Int = 0,
        var previewAtMs: Long = 0,
    )
    private val mutex = Mutex()
    private val active = mutableMapOf<String, Active>()
    private val mutableSessions = MutableStateFlow<List<InkSession>>(emptyList())
    val sessions: StateFlow<List<InkSession>> = mutableSessions.asStateFlow()
    private val mutablePreviews = MutableStateFlow<Map<String, InkStroke>>(emptyMap())
    val previews: StateFlow<Map<String, InkStroke>> = mutablePreviews.asStateFlow()
    private data class Selected(val revision: String, val context: InkSelectionContext)
    private val selected = mutableMapOf<String, Selected>()

    /** Same active interaction only, re-resolved against canonical source on every read. */
    suspend fun selection(sessionId: String, interactionId: String): InkSelectionContext? = mutex.withLock {
        if (!activation.enabled()) { clear(); return@withLock null }
        expire()
        val current = active[sessionId] ?: return@withLock null
        val session = current.session.takeIf { it.interactionId == interactionId } ?: return@withLock null
        if (registry.getDevice(session.inputOwner)?.inkAssociation?.certificateSha256 != current.certificateSha256)
            return@withLock null
        val value = selected[sessionId] ?: return@withLock null
        if (checkpoints.selectionIsCurrent(session, value.revision, value.context)) value.context else {
            selected.remove(sessionId); null
        }
    }

    /** Host security UI only: called after explicit local certificate verification/confirmation. */
    suspend fun associate(identity: DeviceIdentity, association: InkAssociation): Boolean = mutex.withLock {
        if (!activation.enabled()) { clear(); return@withLock false }
        require(identity.deviceId == association.endpoint.deviceId)
        val previous = registry.getDevice(identity.deviceId)
        stopDevice(identity.deviceId)
        registry.saveDevice((previous ?: DeviceRecord(identity)).copy(
            autoConnect = false, inkAssociation = association))
        true
    }

    suspend fun forget(deviceId: String) = mutex.withLock {
        // Revocation and stop are always available, including when qualification is withdrawn.
        stopDevice(deviceId)
        registry.getDevice(deviceId)?.let { registry.saveDevice(it.copy(inkAssociation = null)) }
    }

    /** Caller must resolve the current CognitiveSession's interaction and selected Canvas IDs. */
    suspend fun start(deviceId: String, displayOwner: String, processingOwner: String,
        interactionId: String, boardId: String, documentId: String): InkSession? = mutex.withLock {
        if (!activation.enabled()) { clear(); return@withLock null }
        listOf(deviceId, displayOwner, processingOwner, interactionId, boardId, documentId).forEach(::requireInkReference)
        val association = registry.getDevice(deviceId)?.inkAssociation ?: return@withLock null
        expire()
        stopDevice(deviceId)
        if (active.size >= MAX_SESSIONS) return@withLock null
        val now = nowMs()
        if (now < 0 || now > Long.MAX_VALUE - SESSION_LIFETIME_MS) return@withLock null
        val session = InkSession(UUID.randomUUID().toString(), deviceId, displayOwner, processingOwner,
            interactionId, boardId, documentId, now, now + SESSION_LIFETIME_MS)
        active[session.sessionId] = Active(session, association.certificateSha256, windowStart = now)
        publish()
        session
    }

    suspend fun stop(sessionId: String): Boolean = mutex.withLock {
        val removed = active.remove(sessionId) != null
        selected.remove(sessionId)
        mutablePreviews.value = mutablePreviews.value - sessionId
        publish(); removed
    }

    /** Called on transport loss/host background; saved Canvas ink remains local and editable. */
    suspend fun disconnect(deviceId: String) = mutex.withLock { stopDevice(deviceId) }

    suspend fun refresh() = mutex.withLock {
        if (!activation.enabled()) clear() else expire()
    }

    suspend fun authenticatedSession(sessionId: String, certificateSha256: String): InkSession? = mutex.withLock {
        if (!activation.enabled()) { clear(); return@withLock null }
        expire()
        val current = active[sessionId] ?: return@withLock null
        current.session.takeIf { current.certificateSha256 == certificateSha256 &&
            registry.getDevice(it.inputOwner)?.inkAssociation?.certificateSha256 == certificateSha256 }
    }

    /**
     * Fingerprint comes from the mutually authenticated TLS session, NEVER from a wire field.
     * The lock serializes stop/revocation with durable writes. Frames cannot queue inside this
     * owner: host transport must have at most one outstanding durable frame per connection.
     */
    suspend fun receive(authenticatedCertificateSha256: String, frame: ByteArray): InkReceiveReceipt {
        // Copy at ingress so an asynchronous caller cannot mutate accepted data while suspended.
        if (frame.size > InkEventCodec.MAX_FRAME_BYTES) return InkReceiveReceipt(InkReceiveStatus.MALFORMED)
        val frozenFrame = frame.copyOf()
        return mutex.withLock {
            fun result(status: InkReceiveStatus) = InkReceiveReceipt(status)
            if (!activation.enabled()) { clear(); return@withLock result(InkReceiveStatus.DISABLED) }
            expire()
            val event = InkEventCodec.decode(frozenFrame) ?: return@withLock result(InkReceiveStatus.MALFORMED)
            val current = active[event.sessionId] ?: return@withLock result(InkReceiveStatus.STOPPED)
            val session = current.session
            val association = registry.getDevice(session.inputOwner)?.inkAssociation
            if (association == null || association.certificateSha256 != authenticatedCertificateSha256 ||
                current.certificateSha256 != authenticatedCertificateSha256 ||
                event.sourceDeviceId != session.inputOwner) return@withLock result(InkReceiveStatus.UNTRUSTED)
            if (event.interactionId != session.interactionId || event.boardId != session.boardId ||
                event.documentId != session.documentId) return@withLock result(InkReceiveStatus.STALE)
            val now = nowMs()
            if (event.sentAtMs < session.startedAtMs || event.sentAtMs > now + FUTURE_SKEW_MS ||
                now - event.sentAtMs > MAX_EVENT_AGE_MS) return@withLock result(InkReceiveStatus.STALE)
            if (now - current.windowStart >= 1000) {
                current.windowStart = now; current.previewCount = 0; current.checkpointCount = 0
            }
            when (val payload = event.payload) {
                is InkEventPayload.Checkpoint -> {
                    if (event.sequence < current.checkpointSequence) return@withLock result(InkReceiveStatus.STALE)
                    if (++current.checkpointCount > MAX_CHECKPOINTS_PER_SECOND) return@withLock result(InkReceiveStatus.RATE_LIMITED)
                    val digest = MessageDigest.getInstance("SHA-256").digest(frozenFrame)
                        .joinToString("") { "%02x".format(it) }
                    val receipt = checkpoints.commit(InkCheckpointRequest(session, event.sequence,
                        event.expectedRevision, digest, payload.document))
                    if (receipt.status == InkReceiveStatus.SAVED || receipt.status == InkReceiveStatus.DUPLICATE) {
                        requireNotNull(receipt.revision)
                        current.checkpointSequence = event.sequence
                        current.previewSequence = maxOf(current.previewSequence, event.sequence)
                        mutablePreviews.value = mutablePreviews.value - session.sessionId
                        selected.remove(session.sessionId)
                    }
                    receipt
                }
                is InkEventPayload.Preview -> {
                    if (event.sequence <= maxOf(current.previewSequence, current.checkpointSequence))
                        return@withLock result(InkReceiveStatus.STALE)
                    if (++current.previewCount > MAX_PREVIEWS_PER_SECOND) return@withLock result(InkReceiveStatus.RATE_LIMITED)
                    current.previewSequence = event.sequence
                    current.previewAtMs = now
                    mutablePreviews.value = mutablePreviews.value + (session.sessionId to payload.stroke)
                    result(InkReceiveStatus.PREVIEW)
                }
                InkEventPayload.ClearPreview, InkEventPayload.Heartbeat -> {
                    if (event.sequence <= maxOf(current.previewSequence, current.checkpointSequence))
                        return@withLock result(InkReceiveStatus.STALE)
                    current.previewSequence = event.sequence
                    if (++current.previewCount > MAX_PREVIEWS_PER_SECOND) return@withLock result(InkReceiveStatus.RATE_LIMITED)
                    if (payload == InkEventPayload.ClearPreview) mutablePreviews.value = mutablePreviews.value - session.sessionId
                    result(InkReceiveStatus.PREVIEW)
                }
                is InkEventPayload.Selection -> {
                    if (event.sequence <= maxOf(current.previewSequence, current.checkpointSequence))
                        return@withLock result(InkReceiveStatus.STALE)
                    if (++current.previewCount > MAX_PREVIEWS_PER_SECOND) return@withLock result(InkReceiveStatus.RATE_LIMITED)
                    if (payload.context.workspaceObjectId != session.boardId || payload.context.pageId != session.documentId ||
                        payload.context.createdAtMs < session.startedAtMs || payload.context.updatedAtMs > event.sentAtMs ||
                        !checkpoints.selectionIsCurrent(session, event.expectedRevision, payload.context))
                        return@withLock result(InkReceiveStatus.STALE)
                    current.previewSequence = event.sequence
                    selected[session.sessionId] = Selected(event.expectedRevision, payload.context)
                    result(InkReceiveStatus.PREVIEW)
                }
            }
        }
    }

    private fun stopDevice(deviceId: String) {
        val ids = active.values.filter { it.session.inputOwner == deviceId }.map { it.session.sessionId }
        ids.forEach(active::remove)
        ids.forEach(selected::remove)
        mutablePreviews.value = mutablePreviews.value - ids.toSet()
        publish()
    }
    private fun clear() { active.clear(); selected.clear(); mutablePreviews.value = emptyMap(); publish() }
    private fun publish() { mutableSessions.value = active.values.map { it.session } }
    private fun expire() {
        val now = nowMs()
        val stalePreviews = active.values.filter { now - it.previewAtMs > PREVIEW_LIFETIME_MS }.map { it.session.sessionId }.toSet()
        mutablePreviews.value = mutablePreviews.value - stalePreviews
        val ids = active.values.filter { now < it.session.startedAtMs || now >= it.session.expiresAtMs }
            .map { it.session.sessionId }
        ids.forEach(active::remove)
        ids.forEach(selected::remove)
        mutablePreviews.value = mutablePreviews.value - ids.toSet()
        publish()
    }
    companion object {
        const val MAX_SESSIONS = 4
        const val SESSION_LIFETIME_MS = 15 * 60 * 1000L
        const val MAX_EVENT_AGE_MS = 30_000L
        const val FUTURE_SKEW_MS = 5_000L
        const val MAX_PREVIEWS_PER_SECOND = 120
        const val MAX_CHECKPOINTS_PER_SECOND = 8
        const val PREVIEW_LIFETIME_MS = 2000L
    }
}
