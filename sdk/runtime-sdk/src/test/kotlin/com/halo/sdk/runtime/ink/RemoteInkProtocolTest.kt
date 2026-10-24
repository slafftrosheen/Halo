package com.halo.sdk.runtime.ink

import org.junit.Assert.*
import org.junit.Test

class RemoteInkProtocolTest {
    private val stroke = InkStroke(InkTool.STYLUS, listOf(InkPoint(1, 2, pressure = 500)), "pen-1")
    private fun event(payload: InkEventPayload = InkEventPayload.Checkpoint(InkDocument(listOf(stroke)))) =
        InkEventEnvelope(sourceDeviceId = "tablet", sessionId = "session", interactionId = "interaction",
            boardId = "board", documentId = "document", sequence = 1, sentAtMs = 100,
            expectedRevision = "revision", payload = payload)

    @Test fun checkpointAndPreviewRoundTripPreserveAxesAndIdentity() {
        val selection = InkSelectionContext("board", "document", InkSelectionBounds(0, 0, 10, 10),
            listOf("pen-1"), "revision", InkSelectionOrigin.BOX, 90, 100)
        listOf(event(), event(InkEventPayload.Preview(stroke)), event(InkEventPayload.ClearPreview),
            event(InkEventPayload.Heartbeat), event(InkEventPayload.Selection(selection))).forEach {
            assertEquals(it, InkEventCodec.decode(InkEventCodec.encode(it)))
        }
    }

    @Test fun rejectsFutureVersionTruncationTrailingDataAndOversizeBeforeParsing() {
        val bytes = InkEventCodec.encode(event())
        assertNull(InkEventCodec.decode(bytes.copyOf(bytes.size - 1)))
        assertNull(InkEventCodec.decode(bytes + byteArrayOf(0)))
        assertNull(InkEventCodec.decode(ByteArray(InkEventCodec.MAX_FRAME_BYTES + 1)))
        bytes[7] = 2 // version is the second network-order integer
        assertNull(InkEventCodec.decode(bytes))
    }

    @Test fun rejectsUnboundedReferencesInvalidSequenceAndPreviewCapacity() {
        assertThrows(IllegalArgumentException::class.java) { event().copy(sequence = 0) }
        assertThrows(IllegalArgumentException::class.java) { event().copy(sourceDeviceId = "x".repeat(201)) }
        assertThrows(IllegalArgumentException::class.java) {
            InkEventPayload.Preview(stroke.copy(points = List(257) { InkPoint(1, 2) }))
        }
    }

    @Test fun snapshotsCannotBeChangedThroughCallerOwnedLists() {
        val points = mutableListOf(InkPoint(1, 2))
        val strokes = mutableListOf(InkStroke(InkTool.STYLUS, points, "pen"))
        val bytes = InkEventCodec.encode(event(InkEventPayload.Checkpoint(InkDocument(strokes))))
        points.clear(); strokes.clear()
        val decoded = InkEventCodec.decode(bytes)!!
        assertEquals(1, (decoded.payload as InkEventPayload.Checkpoint).document.strokes.single().points.size)
    }
}
