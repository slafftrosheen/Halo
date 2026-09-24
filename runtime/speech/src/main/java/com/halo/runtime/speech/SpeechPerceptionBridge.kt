package com.halo.runtime.speech

import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.sdk.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

import com.halo.engines.speech.*

/**
 * Bridges domain specific speech events into generalized runtime observations.
 */
class SpeechPerceptionBridge(
    private val eventBus: EventBus,
    private val scope: CoroutineScope,
    private val gate: SpeechTranscriptGate = SpeechTranscriptGate()
) {
    private var bridgeJob: Job? = null

    fun start() {
        if (bridgeJob?.isActive == true) return

        bridgeJob = scope.launch {
            eventBus.observe(SpeechRecognized::class.java).collect { event ->
                val quality = SpeechRecognitionQuality(
                    confidence = event.confidence,
                    averageLogProbability = event.quality.averageTokenLogProbability,
                    audioDurationMs = event.quality.audioDurationMs ?: 0L,
                    voicedDurationMs = event.quality.voicedDurationMs ?: 0L,
                    voicedRatio = event.quality.voicedRatio,
                    peakAmplitude = event.quality.peakAmplitude,
                    language = event.locale,
                    qualityEstimator = event.quality.qualityEstimator ?: "UNKNOWN",
                    source = event.input?.route?.toString() ?: "Phone"
                )

                val eval = gate.evaluate(event.text, quality)
                if (eval is SpeechGateResult.Rejected) {
                    println("SPEECH_RESULT_REJECTED capture=${event.input?.captureId} reason=${eval.reason} durationMs=${quality.audioDurationMs} voicedMs=${quality.voicedDurationMs} voicedRatio=${quality.voicedRatio} estimator=${quality.qualityEstimator} confidence=${event.confidence}")
                    eventBus.publish(
                        SpeechRejected(
                            text = event.text,
                            reason = eval.reason.name,
                            captureId = event.input?.captureId
                        )
                    )
                    return@collect
                }

                val accepted = eval as SpeechGateResult.Accepted
                println("SPEECH_RESULT_ACCEPTED capture=${event.input?.captureId} confidence=${accepted.quality.confidence}")

                val observationId = UUID.randomUUID().toString()
                val interactionId = event.input?.captureId ?: event.id
                val sourceDeviceId = (event.input?.route as? SpeechInputRoute.Wearable)?.deviceId

                val observation = Observation(
                    id = ObservationId(observationId),
                    timestamp = event.timestamp,
                    modality = Modality.AUDIO,
                    source = Source.MICROPHONE,
                    sourceDeviceId = sourceDeviceId,
                    origin = ObservationOrigin(
                        event.input?.source ?: com.halo.sdk.host.ObservationSource.AndroidPhoneMic,
                        sourceDeviceId,
                    ),
                    confidence = accepted.quality.confidence
                        ?: UNKNOWN_RECOGNITION_OBSERVATION_CONFIDENCE,
                    payload = SpeechRecognizedPayload(accepted.transcript, event.locale),
                    metadata = buildMap {
                        put("engine", "SpeechPerceptionBridge")
                        put("captureId", interactionId)
                        put("interactionId", interactionId)
                        put("inputRoute", event.input?.route ?: SpeechInputRoute.Phone)
                        event.confidence?.let { put("recognitionConfidence", it) }
                        put("recognitionQuality", event.quality)
                    },
                    interactionId = interactionId,
                )

                // Publish to EventBus for reactive observation
                eventBus.publish(
                    ObservationCreated(
                        id = UUID.randomUUID().toString(),
                        observation = observation
                    )
                )
            }
        }
    }

    fun stop() {
        bridgeJob?.cancel()
        bridgeJob = null
    }

    private companion object {
        const val UNKNOWN_RECOGNITION_OBSERVATION_CONFIDENCE = 0.5f
    }
}
