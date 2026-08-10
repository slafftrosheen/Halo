package com.halo.drivers.heycyan.media.event

import com.halo.drivers.heycyan.media.MediaLocation
import com.halo.drivers.heycyan.media.repository.HeyCyanMediaType

/**
 * Metadata events emitted during HeyCyan media synchronization.
 *
 * DO NOT pass binary bytes or raw byte buffers through events.
 * Only pass metadata identifiers, types, locations, and sizes.
 */
sealed interface HeyCyanMediaEvent {

    data class Discovered(
        val mediaId: String,
        val type: HeyCyanMediaType,
        val filename: String,
    ) : HeyCyanMediaEvent

    data class Downloaded(
        val mediaId: String,
        val type: HeyCyanMediaType,
        val localLocation: MediaLocation,
        val sizeBytes: Long,
    ) : HeyCyanMediaEvent

    data class Failed(
        val mediaId: String,
        val type: HeyCyanMediaType,
        val reason: String,
    ) : HeyCyanMediaEvent
}

/**
 * Adapter mapping [HeyCyanMediaEvent] to structured metadata maps for Halo Timeline observations.
 */
class HeyCyanMediaTimelineAdapter {

    data class TimelineObservation(
        val type: String,
        val summary: String,
        val metadata: Map<String, String>,
    )

    fun toObservation(event: HeyCyanMediaEvent): TimelineObservation = when (event) {
        is HeyCyanMediaEvent.Discovered -> TimelineObservation(
            type = "heycyan.media.discovered",
            summary = "Discovered ${event.type.name.lowercase()} file: ${event.filename}",
            metadata = mapOf(
                "mediaId" to event.mediaId,
                "type" to event.type.name,
                "filename" to event.filename,
            )
        )

        is HeyCyanMediaEvent.Downloaded -> TimelineObservation(
            type = "heycyan.media.downloaded",
            summary = "Downloaded ${event.type.name.lowercase()} file from HeyCyan (${event.sizeBytes} bytes)",
            metadata = mapOf(
                "mediaId" to event.mediaId,
                "type" to event.type.name,
                "location" to event.localLocation.path,
                "sizeBytes" to event.sizeBytes.toString(),
            )
        )

        is HeyCyanMediaEvent.Failed -> TimelineObservation(
            type = "heycyan.media.failed",
            summary = "Failed downloading ${event.type.name.lowercase()} file: ${event.reason}",
            metadata = mapOf(
                "mediaId" to event.mediaId,
                "type" to event.type.name,
                "reason" to event.reason,
            )
        )
    }
}
