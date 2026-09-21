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
                    source = event.input?.route?.toString() ?: "Phone"
                )

                val eval = gate.evaluate(event.text, quality)
                if (eval is SpeechGateResult.Rejected) {
                    println("SPEECH_RESULT_REJECTED capture=${event.input?.captureId} reason=${eval.reason} confidence=${event.confidence}")
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

                val observation = Observation(
                    id = ObservationId(observationId),
                    timestamp = event.timestamp,
                    modality = Modality.AUDIO,
                    source = Source.MICROPHONE,
                    sourceDeviceId = (event.input?.route as? SpeechInputRoute.Wearable)?.deviceId,
                    origin = ObservationOrigin(event.input?.source ?: com.halo.sdk.host.ObservationSource.AndroidPhoneMic),
                    confidence = accepted.quality.confidence ?: 0.8f,
                    payload = SpeechRecognizedPayload(accepted.transcript, event.locale),
                    metadata = mapOf(
                        "engine" to "SpeechPerceptionBridge",
                        "captureId" to (event.input?.captureId ?: event.id),
                        "inputRoute" to (event.input?.route ?: SpeechInputRoute.Phone)
                    )
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
}
