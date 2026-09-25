package com.halo.app.interaction

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.interaction.InteractionModality
import com.halo.runtime.cognition.interaction.InteractionSurface
import com.halo.runtime.cognition.interaction.InteractionSpeechPresentation
import com.halo.sdk.runtime.SpeechInputRoute
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.TtsRequested

/**
 * RUNTIME-019 voice presentation adapter (specs #36, #37, #38, #39).
 *
 * Bridges simple surfaces onto Halo's EXISTING speech runtime: it derives a
 * spoken prompt and emits [TtsRequested] for the origin device. It never
 * instantiates another recognizer/TTS engine; the user's spoken answer enters
 * through the normal recognition path and becomes an [InteractionResponse]
 * via the host presenter.
 */
class VoiceInteractionSurfaceAdapter(
    private val publishTts: suspend (TtsRequested) -> Unit,
) {

    /**
     * Modality eligibility (pure policy): only CONFIRMATION, simple CHOICE,
     * short TEXT_INPUT and simple TEMPORAL_INPUT are voice-suitable. File
     * pickers and complex input are never read aloud.
     */
    fun isVoiceEligible(surface: InteractionSurface): Boolean {
        if (InteractionModality.VOICE !in surface.presentation.preferredModalities &&
            surface.presentation.requiredModalities.isNotEmpty() &&
            InteractionModality.VISUAL in surface.presentation.requiredModalities
        ) {
            return false // visual confirmation explicitly required (spec #98)
        }
        return when (val content = surface.content) {
            is com.halo.runtime.cognition.interaction.InteractionContent.Confirmation -> true
            is com.halo.runtime.cognition.interaction.InteractionContent.Choice -> content.options.size <= 4 && !content.allowMultiple
            is com.halo.runtime.cognition.interaction.InteractionContent.TextInput -> !content.constraints.sensitive &&
                content.constraints.maxLength <= 200
            is com.halo.runtime.cognition.interaction.InteractionContent.TemporalInput -> true
            else -> false
        }
    }

    /** Semantic spoken prompt; falls back to a derived one when metadata is absent. */
    fun spokenPrompt(surface: InteractionSurface): String {
        surface.speech?.let { return it.concisePrompt ?: it.prompt }
        return when (val content = surface.content) {
            is com.halo.runtime.cognition.interaction.InteractionContent.Confirmation -> content.subject
            is com.halo.runtime.cognition.interaction.InteractionContent.Choice ->
                content.prompt + " " + content.options.joinToString(separator = ", ") { it.label }
            is com.halo.runtime.cognition.interaction.InteractionContent.TextInput -> content.prompt
            is com.halo.runtime.cognition.interaction.InteractionContent.TemporalInput -> content.prompt
            else -> surface.title ?: surface.message ?: ""
        }
    }

    /**
     * Origin-device affinity (spec #38): the TTS target is the device the
     * interaction originated from — earbuds hear the question, earbuds answer.
     */
    suspend fun speak(surface: InteractionSurface, originDevice: SpeechOutputTarget?) {
        if (!isVoiceEligible(surface)) return
        val target = originDevice ?: SpeechOutputTarget.Phone
        val prompt = spokenPrompt(surface)
        publishTts(
            TtsRequested(
                text = prompt,
                transactionId = surface.id.value,
                target = target,
                fallback = if (target is SpeechOutputTarget.Device) {
                    SpeechOutputFallback.DISALLOW_PHONE
                } else {
                    SpeechOutputFallback.ALLOW_PHONE
                },
                sourceCaptureId = null,
                interactionId = surface.interactionId?.value ?: surface.id.value,
                timestamp = System.currentTimeMillis(),
            )
        )
    }
}
