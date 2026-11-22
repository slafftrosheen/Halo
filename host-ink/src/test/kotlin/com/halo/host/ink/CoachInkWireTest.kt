package com.halo.host.ink

import com.halo.sdk.runtime.ink.InkDocument
import com.halo.sdk.runtime.ink.InkEventEnvelope
import com.halo.sdk.runtime.ink.InkEventPayload
import com.halo.sdk.runtime.ink.InkPoint
import com.halo.sdk.runtime.ink.InkStroke
import com.halo.sdk.runtime.ink.InkTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachInkWireTest {
    private val worksheet = CoachInkContent("27 + 15", "Ваш ответ", listOf("Сложите единицы → 12"), true)

    @Test
    fun derivedWorksheetSurvivesRoundTripAndRejectsUnknownBytes() {
        val reply = CoachInkReply(CoachInkPage(4, worksheet), true)
        val frame = CoachInkWire.encode(reply)
        assertEquals(reply, CoachInkWire.decode(frame))
        assertNull(CoachInkWire.decode(frame + byteArrayOf(0)))
        assertNull(CoachInkWire.decode(frame.copyOf(12)))
        assertThrows(IllegalArgumentException::class.java) {
            CoachInkContent("x".repeat(361), "Answer", emptyList(), true)
        }
    }

    @Test
    fun requestMustMatchPairedSenderSessionDocumentAndNextSequence() {
        val session = CoachInkWire.sessionId("00271828")
        val event = InkEventEnvelope(sourceDeviceId = "paired-device", sessionId = session,
            interactionId = session, boardId = "coach", documentId = "scratch", sequence = 3,
            sentAtMs = 1L, expectedRevision = "page-4", payload = InkEventPayload.Heartbeat)
        assertTrue(CoachInkWire.scopedRequest(event, session, "paired-device", 3))
        assertFalse(CoachInkWire.scopedRequest(event, session, "different-device", 3))
        assertFalse(CoachInkWire.scopedRequest(event, session, "paired-device", 4))
        assertFalse(CoachInkWire.scopedRequest(event.copy(documentId = "canvas"), session, "paired-device", 3))
        assertFalse(CoachInkWire.scopedRequest(event, CoachInkWire.sessionId("00271829"), "paired-device", 3))
        assertThrows(IllegalArgumentException::class.java) { CoachInkWire.sessionId("１２３４５６７８") }
    }

    @Test
    fun writingBelongsToOneExactWritableCoachPage() {
        val page = CoachInkPage(4, worksheet)
        val writing = InkDocument(listOf(InkStroke(InkTool.STYLUS, listOf(InkPoint(12, 34)), "writing")))
        assertTrue(CoachInkWire.acceptsWriting(page, "page-4", writing))
        assertFalse(CoachInkWire.acceptsWriting(page, "page-4", InkDocument()))
        assertFalse(CoachInkWire.acceptsWriting(page.copy(version = 5), "page-4", writing))
        assertFalse(CoachInkWire.acceptsWriting(page.copy(content = worksheet.copy(acceptsWriting = false)),
            "page-4", writing))
    }
}
