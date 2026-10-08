package com.halo.runtime.cognition.interaction

/** A bounded speech envelope into the existing first-wins handler; no permission authority. */
object PendingSpeechResponse {
    fun parse(surface: InteractionSurface, transcript: String): InteractionResponse? {
        if (surface.inputRequestId == null || InteractionModality.VISUAL in surface.presentation.requiredModalities) return null
        val text = transcript.trim()
        return when (val content = surface.content) {
            is InteractionContent.Confirmation -> when (text.lowercase()) {
                "yes", "approve", "confirm" -> InteractionResponse.Confirmed(surface.id)
                "no", "deny" -> InteractionResponse.Denied(surface.id)
                "cancel" -> InteractionResponse.Cancelled(surface.id)
                else -> null
            }
            is InteractionContent.TextInput -> if (!content.constraints.sensitive && text.isNotBlank()) InteractionResponse.TextSubmitted(surface.id, text) else null
            else -> null
        }
    }
}
