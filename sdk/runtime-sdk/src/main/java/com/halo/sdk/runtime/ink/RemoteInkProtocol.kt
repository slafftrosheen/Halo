package com.halo.sdk.runtime.ink

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Capabilities are observations of this input surface, never promises about a device family. */
data class InkEndpoint(
    val deviceId: String,
    val pressure: Boolean = false,
    val tilt: Boolean = false,
    val eraser: Boolean = false,
    val hover: Boolean = false,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0,
) {
    init {
        requireInkReference(deviceId)
        require(width in 1..65535 && height in 1..65535)
        require(rotationDegrees in setOf(0, 90, 180, 270))
    }
}

sealed interface InkEventPayload {
    /** Full document checkpoint includes erasures; no remote editing commands or executable text. */
    data class Checkpoint(val document: InkDocument) : InkEventPayload {
        init { InkCodec.encode(document) }
    }
    data class Preview(val stroke: InkStroke) : InkEventPayload {
        init {
            require(stroke.points.size <= MAX_PREVIEW_POINTS)
            InkLimits.requireValid(InkDocument(listOf(stroke)))
        }
    }
    data object ClearPreview : InkEventPayload
    data object Heartbeat : InkEventPayload
    data class Selection(val context: InkSelectionContext) : InkEventPayload

    companion object { const val MAX_PREVIEW_POINTS = 256 }
}

/** No platform pointer object and no tool authority crosses this boundary. */
data class InkEventEnvelope(
    val schemaVersion: Int = 1,
    val sourceDeviceId: String,
    val sessionId: String,
    val interactionId: String,
    val boardId: String,
    val documentId: String,
    val sequence: Long,
    val sentAtMs: Long,
    val expectedRevision: String,
    val payload: InkEventPayload,
) {
    init {
        require(schemaVersion == 1)
        listOf(sourceDeviceId, sessionId, interactionId, boardId, documentId, expectedRevision)
            .forEach(::requireInkReference)
        require(sequence in 1 until Long.MAX_VALUE && sentAtMs >= 0)
    }
}

internal fun requireInkReference(value: String) {
    require(value.length in 1..200 && value.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' })
}

/** Length checks happen before allocating a payload. Unknown fields/versions are rejected. */
object InkEventCodec {
    const val MAX_FRAME_BYTES = InkLimits.MAX_ENCODED_CHARS + 4096
    private const val MAGIC = 0x48494E4B // HINK

    fun encode(event: InkEventEnvelope): ByteArray {
        val content = when (val payload = event.payload) {
            is InkEventPayload.Checkpoint -> InkCodec.encode(payload.document)
            is InkEventPayload.Preview -> {
                require(payload.stroke.points.size <= InkEventPayload.MAX_PREVIEW_POINTS)
                InkCodec.encode(InkDocument(listOf(payload.stroke)))
            }
            InkEventPayload.ClearPreview -> ""
            InkEventPayload.Heartbeat -> ""
            is InkEventPayload.Selection -> with(payload.context) {
                listOf(workspaceObjectId, pageId?.let { "P$it" } ?: "N", sourceRevision, origin.name,
                    createdAtMs, updatedAtMs, bounds.left, bounds.top, bounds.right, bounds.bottom,
                    selectedStrokeIds.joinToString(",")).joinToString("|")
            }
        }.toByteArray(Charsets.US_ASCII)
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(MAGIC); out.writeInt(event.schemaVersion)
                listOf(event.sourceDeviceId, event.sessionId, event.interactionId, event.boardId,
                    event.documentId, event.expectedRevision).forEach(out::writeUTF)
                out.writeLong(event.sequence); out.writeLong(event.sentAtMs)
                out.writeByte(when (event.payload) {
                    is InkEventPayload.Checkpoint -> 1
                    is InkEventPayload.Preview -> 2
                    InkEventPayload.ClearPreview -> 3
                    InkEventPayload.Heartbeat -> 4
                    is InkEventPayload.Selection -> 5
                })
                out.writeInt(content.size); out.write(content)
            }
            bytes.toByteArray().also { require(it.size <= MAX_FRAME_BYTES) }
        }
    }

    fun decode(bytes: ByteArray): InkEventEnvelope? {
        if (bytes.size !in 1..MAX_FRAME_BYTES) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == MAGIC && input.readInt() == 1)
                val ids = List(6) {
                    // readUTF's allocation is bounded further by the total frame limit.
                    input.readUTF().also(::requireInkReference)
                }
                val sequence = input.readLong(); val sentAt = input.readLong()
                val kind = input.readUnsignedByte()
                val size = input.readInt()
                require(size in 0..InkLimits.MAX_ENCODED_CHARS && size == input.available())
                val content = ByteArray(size).also(input::readFully)
                require(content.all { it in 0..127 })
                val document = if (kind in 3..5) null else InkCodec.decode(content.toString(Charsets.US_ASCII))
                val payload = when (kind) {
                    1 -> InkEventPayload.Checkpoint(requireNotNull(document))
                    2 -> InkEventPayload.Preview(requireNotNull(document).strokes.single())
                    3 -> { require(size == 0); InkEventPayload.ClearPreview }
                    4 -> { require(size == 0); InkEventPayload.Heartbeat }
                    5 -> {
                        val fields = content.toString(Charsets.US_ASCII).split('|', limit = 12)
                        require(fields.size == 11)
                        val page = when {
                            fields[1] == "N" -> null
                            fields[1].startsWith("P") -> fields[1].drop(1)
                            else -> return null
                        }
                        InkEventPayload.Selection(InkSelectionContext(fields[0], page,
                            InkSelectionBounds(fields[6].toInt(), fields[7].toInt(), fields[8].toInt(), fields[9].toInt()),
                            fields[10].split(',', limit = InkLimits.MAX_STROKES + 1), fields[2],
                            InkSelectionOrigin.valueOf(fields[3]), fields[4].toLong(), fields[5].toLong()))
                    }
                    else -> return null
                }
                InkEventEnvelope(sourceDeviceId = ids[0], sessionId = ids[1], interactionId = ids[2],
                    boardId = ids[3], documentId = ids[4], expectedRevision = ids[5],
                    sequence = sequence, sentAtMs = sentAt, payload = payload)
            }
        } catch (_: IllegalArgumentException) { null }
        catch (_: IOException) { null }
        catch (_: NoSuchElementException) { null }
    }
}
