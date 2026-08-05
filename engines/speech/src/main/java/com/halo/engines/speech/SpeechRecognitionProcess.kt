package com.halo.engines.speech

import com.halo.runtime.kernel.Process
import com.halo.runtime.kernel.ProcessState
import com.halo.runtime.kernel.bus.PerceptionBus
import com.halo.icl.ontology.Observation
import com.halo.icl.core.CognitiveId
import com.halo.icl.core.Confidence
import com.halo.icl.core.Provenance
import com.halo.icl.core.CognitiveEdge
import com.halo.icl.core.EdgeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock

class SpeechRecognitionProcess(
    private val perceptionBus: PerceptionBus
) : Process {
    override val id: String = "driver.speech.recognizer"
    override val name: String = "Speech Recognizer"
    
    private val _state = MutableStateFlow(ProcessState.Created)
    override val state: StateFlow<ProcessState> = _state

    override suspend fun start(): Unit = coroutineScope {
        _state.value = ProcessState.Running
        
        launch {
            perceptionBus.subscribe(id)
                .filter { it.modality == "AudioStream" }
                .flowOn(Dispatchers.Default)
                .collect { audioObservation ->
                    // 1. Process audio chunks (Mocked)
                    // 2. Run Voice Activity Detection (VAD) (Moved to AudioPerceptionProcess)
                    // 3. Convert to text via Whisper/HeyCyan
                    
                    // Simulate processing latency based on audio duration (approximate)
                    val rawPcm = audioObservation.rawData as? ShortArray
                    val delayMs = if (rawPcm != null) {
                        // Assume 16kHz, 1 second = 16,000 samples. Processing takes ~25% of audio length
                        ((rawPcm.size / 16000f) * 250f).toLong().coerceAtLeast(300L)
                    } else {
                        500L
                    }
                    
                    kotlinx.coroutines.delay(delayMs)
                    
                    // Here we emit a fake transcript for the MVP loop validation
                    val textObservation = Observation(
                        id = CognitiveId(),
                        created = Clock.System.now(),
                        confidence = Confidence(0.92f),
                        provenance = Provenance(id, audioObservation.id),
                        edges = listOf(CognitiveEdge(audioObservation.id, EdgeType.Derived)),
                        rawData = "Open Spotify", // Hardcoded mock for Alpha test
                        modality = "Speech"
                    )
                    
                    perceptionBus.publish(id, textObservation)
                }
        }
    }

    override suspend fun pause() { _state.value = ProcessState.Paused }
    override suspend fun resume() { _state.value = ProcessState.Running }
    override suspend fun stop() { _state.value = ProcessState.Stopped }
}
