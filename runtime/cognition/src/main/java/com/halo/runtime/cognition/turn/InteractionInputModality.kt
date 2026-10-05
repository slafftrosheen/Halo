package com.halo.runtime.cognition.turn

import com.halo.sdk.runtime.Source

/** Human input is independent of output delivery and device route. */
enum class InteractionInputModality { TEXT, VOICE, UNKNOWN }

fun CognitiveTurnRequest.inputModality(): InteractionInputModality {
    metadata["inputModality"]?.let { value ->
        InteractionInputModality.entries.firstOrNull { it.name == value }?.let { return it }
    }
    if (origin?.source == Source.MICROPHONE || origin?.speechRoute != null) return InteractionInputModality.VOICE
    if (metadata["input"] == "keyboard") return InteractionInputModality.TEXT
    return InteractionInputModality.UNKNOWN
}
