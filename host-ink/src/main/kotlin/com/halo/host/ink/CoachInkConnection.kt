package com.halo.host.ink

import com.halo.sdk.runtime.ink.InkActivationPolicy
import com.halo.sdk.runtime.ink.InkDocument
import com.halo.sdk.runtime.ink.InkEventCodec
import com.halo.sdk.runtime.ink.InkEventEnvelope
import com.halo.sdk.runtime.ink.InkEventPayload
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlinx.coroutines.delay

/** Reviewed, derived Coach text. There is no photograph, original OCR or saved object reference on this wire. */
data class CoachInkContent(val problem: String, val phase: String, val steps: List<String>, val acceptsWriting: Boolean) {
    init {
        require(problem.isNotBlank() && problem.length <= 360)
        require(phase.isNotBlank() && phase.length <= 360)
        require(steps.size <= 8 && steps.all { it.isNotBlank() && it.length <= 360 })
    }
}

data class CoachInkPage(val version: Long, val content: CoachInkContent) {
    init { require(version > 0) }
    val revision get() = "page-$version"
}

data class CoachInkReply(val page: CoachInkPage, val acceptedWriting: Boolean)

object CoachInkWire {
    const val CODE_LENGTH = 8
    const val ACCEPT_TIMEOUT_MS = 120_000
    const val SESSION_TIMEOUT_MS = 15 * 60_000L
    private const val MAGIC = 0x48435031 // HCP1
    private const val MAX_REPLY_BYTES = 12_000
    private const val BOARD = "coach"
    private const val DOCUMENT = "scratch"
    private const val INITIAL = "review"

    fun sessionId(code: String): String {
        require(code.length == CODE_LENGTH && code.all { it in '0'..'9' })
        return "coach-$code"
    }

    fun encode(reply: CoachInkReply): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeLong(reply.page.version)
            out.writeBoolean(reply.acceptedWriting)
            val content = reply.page.content
            out.writeUTF(content.problem)
            out.writeUTF(content.phase)
            out.writeBoolean(content.acceptsWriting)
            out.writeByte(content.steps.size)
            content.steps.forEach(out::writeUTF)
        }
        bytes.toByteArray().also { require(it.size <= MAX_REPLY_BYTES) }
    }

    fun decode(frame: ByteArray): CoachInkReply? {
        if (frame.size !in 1..MAX_REPLY_BYTES) return null
        return try {
            DataInputStream(ByteArrayInputStream(frame)).use { input ->
                require(input.readInt() == MAGIC)
                val version = input.readLong()
                val accepted = input.readBoolean()
                val problem = input.readUTF()
                val phase = input.readUTF()
                val writing = input.readBoolean()
                val count = input.readUnsignedByte().also { require(it <= 8) }
                val steps = List(count) { input.readUTF() }
                require(input.available() == 0)
                CoachInkReply(CoachInkPage(version, CoachInkContent(problem, phase, steps, writing)), accepted)
            }
        } catch (_: IllegalArgumentException) { null }
        catch (_: IOException) { null }
    }

    internal fun scopedRequest(event: InkEventEnvelope, sessionId: String, deviceId: String, sequence: Long) =
        event.sourceDeviceId == deviceId && event.sessionId == sessionId && event.interactionId == sessionId &&
            event.boardId == BOARD && event.documentId == DOCUMENT && event.sequence == sequence

    private fun usableWriting(document: InkDocument): Boolean =
        document.strokes.size in 1..200 && document.strokes.sumOf { it.points.size } <= 8_000

    internal fun acceptsWriting(page: CoachInkPage, revision: String, document: InkDocument): Boolean =
        page.content.acceptsWriting && revision == page.revision && usableWriting(document)

    /** Never hands a Coach document to the saved editor or to the Runtime checkpoint store. */
    suspend fun host(channel: InkTlsChannel, sessionId: String, deviceId: String, canContinue: suspend () -> Boolean,
        readPage: () -> CoachInkPage, acceptWriting: (CoachInkPage, InkDocument) -> Boolean) {
        var sequence = 1L
        while (canContinue()) {
            val event = requireNotNull(InkEventCodec.decode(channel.read()))
            require(scopedRequest(event, sessionId, deviceId, sequence) && canContinue())
            if (sequence == 1L) require(event.payload == InkEventPayload.Heartbeat && event.expectedRevision == INITIAL)
            val page = readPage()
            val accepted = when (val payload = event.payload) {
                InkEventPayload.Heartbeat -> false
                is InkEventPayload.Checkpoint -> {
                    val current = acceptsWriting(page, event.expectedRevision, payload.document)
                    current && acceptWriting(page, payload.document)
                }
                else -> error("Unexpected Coach input")
            }
            require(canContinue())
            channel.write(encode(CoachInkReply(readPage(), accepted)))
            sequence++
            delay(200)
        }
    }

    /** One request in flight, no replay and no automatic reconnect. */
    class Client(private val channel: InkTlsChannel, private val sessionId: String, private val deviceId: String,
        private val activation: InkActivationPolicy) {
        private var sequence = 0L
        private var page: CoachInkPage? = null

        suspend fun read(): CoachInkReply = exchange(InkEventPayload.Heartbeat, page?.revision ?: INITIAL)

        suspend fun send(expected: CoachInkPage, document: InkDocument): CoachInkReply {
            require(usableWriting(document))
            return exchange(InkEventPayload.Checkpoint(document), expected.revision)
        }

        private suspend fun exchange(payload: InkEventPayload, revision: String): CoachInkReply {
            check(activation.enabled())
            val event = InkEventEnvelope(sourceDeviceId = deviceId, sessionId = sessionId,
                interactionId = sessionId, boardId = BOARD, documentId = DOCUMENT, sequence = ++sequence,
                sentAtMs = System.currentTimeMillis(), expectedRevision = revision, payload = payload)
            channel.write(InkEventCodec.encode(event))
            val reply = requireNotNull(decode(channel.read()))
            check(activation.enabled() && reply.page.version >= (page?.version ?: 0))
            page = reply.page
            return reply
        }
    }
}
