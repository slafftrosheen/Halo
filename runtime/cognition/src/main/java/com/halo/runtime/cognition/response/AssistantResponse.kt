package com.halo.runtime.cognition.response

import com.halo.cognition.language.InteractionId
import java.time.Instant

enum class ResponsePresentationMode {
    SPOKEN_BRIEF,
    VISUAL_NORMAL,
    VISUAL_DETAILED,
}

sealed interface ResponseConfidence {
    data object Deterministic : ResponseConfidence
    data class Retrieved(val evidenceCount: Int) : ResponseConfidence
    data object Generated : ResponseConfidence
    data class Mixed(val deterministicFacts: Int, val retrievedSources: Int) : ResponseConfidence
}

data class ResponseProvenance(
    val providerId: String? = null,
    val modelId: String? = null,
    val contextObjectIds: List<String> = emptyList(),
    val memoryIds: List<String> = emptyList(),
    val runtimeFacts: List<String> = emptyList(),
    val toolsUsed: List<String> = emptyList(),
    val networkUsed: Boolean = false,
)

data class AssistantResponse(
    val interactionId: InteractionId,
    val fullText: String,
    val spokenText: String? = null,
    val provenance: ResponseProvenance = ResponseProvenance(),
    val confidence: ResponseConfidence = ResponseConfidence.Generated,
    val createdAt: Instant = Instant.now(),
)
