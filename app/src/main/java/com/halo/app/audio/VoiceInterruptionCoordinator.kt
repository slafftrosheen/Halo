package com.halo.app.audio

import com.halo.engines.speech.ListeningStarted
import com.halo.sdk.runtime.*

/** Content-free policy consumer. Does not own capture or another playback engine. */
class VoiceInterruptionCoordinator(
    private val settings: () -> VoiceExperienceSettings,
    private val stopOutput: () -> Unit,
    private val cancelGeneration: () -> Unit,
    private val classifier: BargeInSemanticClassifier? = null,
    private val stopExternal: () -> Unit = {},
    private val log: (String) -> Unit = {},
) {
    private var outputId: String? = null
    private var captureId: String? = null
    suspend fun onEvent(event: HaloEvent) {
        when (event) {
            is TtsStarted -> outputId = event.transactionId
            is TtsCompleted -> finished(event.transactionId)
            is TtsCancelled -> finished(event.transactionId)
            is TtsFailed -> finished(event.transactionId)
            is ListeningStarted -> {
                captureId = event.captureId
                if (event.activation == SpeechActivationKind.EXPLICIT_USER && outputId != null)
                    evaluate(BargeInEvidence(true, false, externalProviderInterrupted = false,
                        timestampMs = event.timestamp), event.activation)
            }
            is SpeechActivityObserved -> if (outputId != null && event.captureId == captureId) {
                val configuration = settings()
                val semantic = if (configuration.semanticInterruptionEnabled && classifier?.available == true)
                    classifier.classify(event.sustainedSpeechMs, event.voicedSpeech, true) else null
                evaluate(BargeInEvidence(false, event.voicedSpeech, event.voicedSpeech, event.inputRouteValid,
                    false, event.timestamp, event.sustainedSpeechMs,
                    semantic?.probability, semantic?.classification, event.sourceDeviceId), SpeechActivationKind.VOICE_ACTIVITY)
            }
        }
    }
    private fun finished(id: String) { if (outputId == id) outputId = null }
    private fun evaluate(evidence: BargeInEvidence, activation: SpeechActivationKind) {
        val configuration = settings()
        val decision = BargeInPolicy.decide(evidence, outputId != null, configuration.interruptionPolicy())
        log("decision=$decision activation=$activation outputActive=${outputId != null} " +
            "durationBucket=${(evidence.sustainedSpeechMs ?: 0) / 100} classifierAvailable=${classifier?.available == true} " +
            "semanticModeEnabled=${configuration.semanticInterruptionEnabled} sourceKind=${if (evidence.sourceDeviceId == null) "PHONE" else "DEVICE"}")
        when (decision) {
            BargeInDecision.ACCEPT -> { outputId = null; stopOutput(); cancelGeneration() }
            BargeInDecision.STOP_EXTERNAL_ONLY -> stopExternal()
            BargeInDecision.REJECT_ECHO -> Unit
        }
    }
}
