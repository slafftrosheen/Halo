package com.halo.engines.speech
import java.util.UUID

data class SpeechSession(
    val id: UUID,
    val startedAt: Long,
    var endedAt: Long?,
    var transcript: String?,
    var confidence: Float
)
