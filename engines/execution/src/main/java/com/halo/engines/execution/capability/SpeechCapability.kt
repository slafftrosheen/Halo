package com.halo.engines.execution.capability

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.CommunicationTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import com.halo.sdk.provider.SpeechRequest
import com.halo.sdk.provider.SpeechSynthesisProvider

/**
 * Host-neutral hook for selecting the physical output route before speech.
 * The runtime only knows that speech output can be prepared; Android/HeyCyan
 * details remain in the host composition root.
 */
fun interface SpeechOutputRouter {
    suspend fun prepareForSpeech(): Result<Unit>
}

class SpeechCapability(
    private val ttsProvider: SpeechSynthesisProvider,
    private val outputRouter: SpeechOutputRouter? = null,
) : Capability {
    override val id: String = "capability.speech"

    override suspend fun supports(task: Task): Boolean = task is CommunicationTask

    override suspend fun execute(task: Task): Outcome {
        if (task !is CommunicationTask) return Outcome.PermanentFailure("Invalid task type")

        val textToSpeak = task.payload
        if (textToSpeak.isBlank()) return Outcome.PermanentFailure("No text provided to speak")

        return try {
            // Routing is best-effort. A disconnected wearable must never make
            // speech unavailable; the Android audio policy can fall back to phone output.
            outputRouter?.let { router ->
                runCatching { router.prepareForSpeech() }
                    .onFailure { /* TTS remains available on the host fallback route. */ }
            }

            val result = ttsProvider.synthesize(SpeechRequest(text = textToSpeak))
            if (result.successful) {
                Outcome.Success("Speech synthesized successfully")
            } else {
                Outcome.PermanentFailure("Speech synthesis failed")
            }
        } catch (e: Exception) {
            Outcome.PermanentFailure("Exception during speech synthesis: ${e.message}", e)
        }
    }
}
