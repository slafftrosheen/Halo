package com.halo.runtime.observer

import com.halo.sdk.runtime.AssistantResponsePublished
import com.halo.sdk.runtime.Modality
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.ObservationCreated
import com.halo.sdk.runtime.ResponseDelivery
import com.halo.sdk.runtime.Source
import com.halo.sdk.runtime.SpeechObservationPayload
import com.halo.sdk.runtime.SpeechRecognized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineEventMapperTest {
    private val mapper = DefaultTimelineEventMapper()

    @Test
    fun `keyboard input becomes one user-facing turn`() {
        val event = ObservationCreated(
            observation = Observation(
                modality = Modality.TEXT,
                source = Source.USER,
                payload = SpeechObservationPayload("Hello Halo", "en-US"),
                timestamp = 1_000L
            ),
            timestamp = 1_001L
        )

        val mapped = mapper.map(event)

        requireNotNull(mapped)
        assertEquals("You", mapped.title)
        assertEquals("Hello Halo", mapped.description)
        assertEquals("Sent", mapped.status)
        assertEquals(1_000L, mapped.timestamp.toEpochMilli())
    }

    @Test
    fun `microphone observation becomes one user-facing turn`() {
        val event = ObservationCreated(
            observation = Observation(
                modality = Modality.AUDIO,
                source = Source.MICROPHONE,
                payload = SpeechObservationPayload("Open maps", "en-US"),
                timestamp = 2_000L
            )
        )

        val mapped = mapper.map(event)

        requireNotNull(mapped)
        assertEquals("You", mapped.title)
        assertEquals("Open maps", mapped.description)
    }

    @Test
    fun `raw speech stage is not duplicated into timeline`() {
        val event = SpeechRecognized(
            timestamp = 3_000L,
            text = "Open maps",
            locale = "en-US"
        )

        assertNull(mapper.map(event))
    }

    @Test
    fun `assistant response becomes user-facing halo turn`() {
        val event = AssistantResponsePublished(
            text = "Opening maps.",
            delivery = ResponseDelivery.VISUAL_ONLY,
            transactionId = "tx-1",
            timestamp = 4_000L
        )

        val mapped = mapper.map(event)

        requireNotNull(mapped)
        assertEquals("Halo", mapped.title)
        assertEquals("Opening maps.", mapped.description)
        assertEquals("Completed", mapped.status)
    }
}
