package com.halo.host.ink

import com.halo.sdk.runtime.ink.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

data class InkSessionOffer(val session: InkSession, val endpoint: InkEndpoint,
    val revision: String, val document: InkDocument) {
    init {
        require(endpoint.deviceId == session.inputOwner)
        require(session.expiresAtMs > session.startedAtMs &&
            session.expiresAtMs - session.startedAtMs <= RemoteInkSessionOwner.SESSION_LIFETIME_MS)
        require(session.displayOwner.matches(Regex("[A-Za-z0-9_-]{1,200}")))
        require(session.processingOwner.matches(Regex("[A-Za-z0-9_-]{1,200}")))
    }
}

/** The explicit approved document snapshot is transferred only after mutual TLS authentication. */
object InkSessionOfferCodec {
    private const val MAGIC = 0x48494F31 // HIO1
    fun encode(offer: InkSessionOffer): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            val session = offer.session
            out.writeInt(MAGIC)
            out.writeUTF(session.displayOwner); out.writeUTF(session.processingOwner)
            out.writeLong(session.expiresAtMs)
            out.writeInt(offer.endpoint.width); out.writeInt(offer.endpoint.height)
            out.writeInt(offer.endpoint.rotationDegrees)
            out.writeBoolean(offer.endpoint.pressure); out.writeBoolean(offer.endpoint.tilt)
            out.writeBoolean(offer.endpoint.eraser); out.writeBoolean(offer.endpoint.hover)
            val content = InkEventCodec.encode(InkEventEnvelope(sourceDeviceId = session.inputOwner,
                sessionId = session.sessionId, interactionId = session.interactionId, boardId = session.boardId,
                documentId = session.documentId, sequence = 1, sentAtMs = session.startedAtMs,
                expectedRevision = offer.revision, payload = InkEventPayload.Checkpoint(offer.document)))
            out.writeInt(content.size); out.write(content)
        }
        bytes.toByteArray().also { require(it.size <= InkEventCodec.MAX_FRAME_BYTES) }
    }

    fun decode(frame: ByteArray): InkSessionOffer? {
        if (frame.size !in 1..InkEventCodec.MAX_FRAME_BYTES) return null
        return try {
            DataInputStream(ByteArrayInputStream(frame)).use { input ->
                require(input.readInt() == MAGIC)
                val display = input.readUTF(); val processing = input.readUTF(); val expires = input.readLong()
                val width = input.readInt(); val height = input.readInt(); val rotation = input.readInt()
                val pressure = input.readBoolean(); val tilt = input.readBoolean()
                val eraser = input.readBoolean(); val hover = input.readBoolean()
                val length = input.readInt()
                require(length in 1..InkEventCodec.MAX_FRAME_BYTES && length == input.available())
                val event = requireNotNull(InkEventCodec.decode(ByteArray(length).also(input::readFully)))
                val document = (event.payload as? InkEventPayload.Checkpoint)?.document ?: return null
                InkSessionOffer(InkSession(event.sessionId, event.sourceDeviceId, display, processing,
                    event.interactionId, event.boardId, event.documentId, event.sentAtMs, expires),
                    InkEndpoint(event.sourceDeviceId, pressure, tilt, eraser, hover, width, height, rotation),
                    event.expectedRevision, document)
            }
        } catch (_: IllegalArgumentException) { null } catch (_: IOException) { null }
    }
}

data class InkWireReceipt(val sequence: Long, val receipt: InkReceiveReceipt)

object InkReceiptCodec {
    fun encode(value: InkWireReceipt): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use {
            it.writeInt(0x48495231) // HIR1
            it.writeLong(value.sequence); it.writeUTF(value.receipt.status.name)
            it.writeUTF(value.receipt.revision.orEmpty())
        }
        bytes.toByteArray()
    }
    fun decode(frame: ByteArray): InkWireReceipt? {
        if (frame.size !in 1..1024) return null
        return try {
            DataInputStream(ByteArrayInputStream(frame)).use {
                require(it.readInt() == 0x48495231)
                val sequence = it.readLong(); require(sequence > 0)
                val status = InkReceiveStatus.valueOf(it.readUTF())
                val revision = it.readUTF().takeIf(String::isNotEmpty)
                require(revision == null || revision.matches(Regex("[A-Za-z0-9_-]{1,200}")))
                require(status !in setOf(InkReceiveStatus.SAVED, InkReceiveStatus.DUPLICATE) || revision != null)
                require(it.available() == 0)
                InkWireReceipt(sequence, InkReceiveReceipt(status, revision))
            }
        } catch (_: IllegalArgumentException) { null } catch (_: IOException) { null }
    }
}

/** Host lifecycle owns the channel/job. Exceptions close it and revoke this ephemeral session. */
object RemoteInkConnection {
    suspend fun receive(channel: InkTlsChannel, owner: RemoteInkSessionOwner, offer: InkSessionOffer) {
        channel.use {
            val session = requireNotNull(owner.authenticatedSession(offer.session.sessionId, channel.peerCertificateSha256))
            require(session == offer.session)
            try {
                channel.write(InkSessionOfferCodec.encode(offer))
                while (true) {
                    val frame = channel.read()
                    val event = requireNotNull(InkEventCodec.decode(frame))
                    require(event.sessionId == session.sessionId)
                    val receipt = owner.receive(channel.peerCertificateSha256, frame)
                    channel.write(InkReceiptCodec.encode(InkWireReceipt(event.sequence, receipt)))
                    if (receipt.status !in setOf(InkReceiveStatus.SAVED, InkReceiveStatus.DUPLICATE, InkReceiveStatus.PREVIEW)) return
                }
            } finally { withContext(NonCancellable) { owner.stop(session.sessionId) } }
        }
    }
}

/**
 * Single writer on the pen host. Retains one exact in-flight checkpoint until an acknowledged
 * commit; retries use byte-identical frames. Caller persists local ink independently and never
 * discards it after transport loss. A fresh explicit session is required after process death.
 */
class RemoteInkPublisher(private val offer: InkSessionOffer, private val nowMs: () -> Long = System::currentTimeMillis) {
    private var sequence = 0L
    private var revision = offer.revision
    private var pending: ByteArray? = null
    private var stopped = false

    fun checkpoint(document: InkDocument): ByteArray {
        check(!stopped && pending == null) { "Wait for the saved-ink receipt" }
        return frame(InkEventPayload.Checkpoint(document)).also { pending = it.copyOf() }
    }
    fun retry(): ByteArray? = pending?.copyOf()
    fun preview(stroke: InkStroke): ByteArray? = if (stopped || pending != null) null else frame(InkEventPayload.Preview(stroke))
    fun clearPreview(): ByteArray? = if (stopped || pending != null) null else frame(InkEventPayload.ClearPreview)
    fun heartbeat(): ByteArray? = if (stopped || pending != null) null else frame(InkEventPayload.Heartbeat)
    fun selection(context: InkSelectionContext): ByteArray? = if (stopped || pending != null) null else frame(InkEventPayload.Selection(context))
    fun stop() { stopped = true; pending = null }
    fun acknowledge(frame: ByteArray): InkReceiveReceipt {
        val ack = requireNotNull(InkReceiptCodec.decode(frame))
        require(ack.sequence == sequence)
        when (ack.receipt.status) {
            InkReceiveStatus.SAVED, InkReceiveStatus.DUPLICATE -> {
                check(pending != null)
                revision = requireNotNull(ack.receipt.revision); pending = null
            }
            InkReceiveStatus.PREVIEW -> check(pending == null)
            else -> stopped = true // Preserve pending bytes for truthful uncertainty/readback.
        }
        return ack.receipt
    }
    private fun frame(payload: InkEventPayload): ByteArray {
        val now = nowMs()
        check(now >= offer.session.startedAtMs && now < offer.session.expiresAtMs)
        check(sequence < Long.MAX_VALUE - 1)
        val session = offer.session
        val bytes = InkEventCodec.encode(InkEventEnvelope(sourceDeviceId = session.inputOwner,
            sessionId = session.sessionId, interactionId = session.interactionId, boardId = session.boardId,
            documentId = session.documentId, sequence = sequence + 1, sentAtMs = now,
            expectedRevision = revision, payload = payload))
        sequence++
        return bytes
    }
}
