package com.halo.sdk.runtime

import java.util.UUID
import com.halo.sdk.host.ObservationSource

@JvmInline
value class ObservationId(val value: String)

enum class Modality {
    AUDIO, VIDEO, TEXT, SENSOR, NETWORK, SYSTEM, UNKNOWN
}

enum class Source {
    MICROPHONE, CAMERA, BLUETOOTH, GPS, OS, USER, SYSTEM
}

/** Stable physical source identity. Kept separate from modality and legacy Source. */
data class ObservationOrigin(
    val source: ObservationSource,
    val hostId: String? = null
)

interface ObservationPayload

data class Observation(
    val id: ObservationId = ObservationId(UUID.randomUUID().toString()),
    val modality: Modality,
    val source: Source,
    val payload: ObservationPayload,
    val origin: ObservationOrigin? = null,
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
)

data class ObservationCreated(
    override val id: String = UUID.randomUUID().toString(),
    val observation: Observation,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent
