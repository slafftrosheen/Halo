package com.halo.runtime.speech

import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.sdk.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Bridges domain specific speech events into generalized runtime observations.
 */
class SpeechPerceptionBridge(
    private val eventBus: EventBus,
    private val scope: CoroutineScope
) {
    private var bridgeJob: Job? = null

    fun start() {
        if (bridgeJob?.isActive == true) return

        bridgeJob = scope.launch {
            eventBus.observe(SpeechRecognized::class.java).collect { event ->
                val observationId = UUID.randomUUID().toString()

                val observation = Observation(
                    id = ObservationId(observationId),
                    timestamp = event.timestamp,
                    modality = Modality.AUDIO,
                    source = Source.MICROPHONE,
                    confidence = 1.0f,
                    payload = SpeechRecognizedPayload(event.text, event.locale),
                    metadata = mapOf("engine" to "SpeechPerceptionBridge")
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
