package com.halo.engines.execution.capability

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import com.halo.sdk.provider.SpeechRequest
import com.halo.sdk.provider.SpeechSynthesisProvider

import com.halo.runtime.cognition.CommunicationTask

class SpeechCapability(
    private val ttsProvider: SpeechSynthesisProvider
) : Capability {
    override val id: String = "capability.speech"

    override suspend fun supports(task: Task): Boolean {
        return task is CommunicationTask
    }

    override suspend fun execute(task: Task): Outcome {
        if (task !is CommunicationTask) return Outcome.PermanentFailure("Invalid task type")
        
        val textToSpeak = task.payload
        if (textToSpeak.isBlank()) return Outcome.PermanentFailure("No text provided to speak")
        
        return try {
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
