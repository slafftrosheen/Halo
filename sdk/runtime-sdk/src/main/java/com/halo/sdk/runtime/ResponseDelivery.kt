package com.halo.sdk.runtime

import java.util.UUID

/**
 * Presentation contract for one completed Halo response.
 *
 * The input transaction owns this decision. Presentation code must not infer
 * whether a response should speak from UI state or from the next event in a
 * global stream.
 */
enum class ResponseDelivery {
    VISUAL_ONLY,
    SPEECH_AND_VISUAL
}

fun Source.defaultResponseDelivery(): ResponseDelivery = when (this) {
    Source.MICROPHONE -> ResponseDelivery.SPEECH_AND_VISUAL
    else -> ResponseDelivery.VISUAL_ONLY
}

data class AssistantResponsePublished(
    override val id: String = UUID.randomUUID().toString(),
    val text: String,
    val delivery: ResponseDelivery,
    val transactionId: String,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent
