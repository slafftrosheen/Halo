package com.halo.engines.speech

import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.sdk.runtime.*
import com.halo.runtime.services.Services
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AudioPerceptionEngine(
    private val audioSource: AudioSource,
    private val vad: VoiceActivityDetector,
    private val preprocessor: AudioPreprocessor,
    private val recognizer: SpeechRecognizer
) : Engine {
    override val metadata = EngineMetadata(
        id = Identifier("engine.perception.audio"),
        version = Version(1, 0, 0),
        description = "Audio Perception Engine"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    private val _speechState = MutableStateFlow<SpeechState>(Idle)
    val speechState: StateFlow<SpeechState> = _speechState

    override val dependencies: Set<ServiceKey<*>> = setOf(Services.EventBus)

    private lateinit var context: EngineContext
    private var job: Job? = null
    private val buffer = AudioBuffer()

    override suspend fun initialize(context: EngineContext) {
        this.context = context
        _state.value = EngineState.Initializing
    }

    override suspend fun start() {
        _state.value = EngineState.Running
        _speechState.value = Listening
        
        val eventBus = context.registry.resolve(Services.EventBus)
        
        job = CoroutineScope(context.runtime.scheduler.io).launch {
            eventBus.publish(ListeningStarted())
            audioSource.start()
            
            var isRecording = false
            
            audioSource.frames.collect { rawFrame ->
                val frame = preprocessor.process(rawFrame)
                
                if (vad.isSpeech(frame)) {
                    if (!isRecording) {
                        isRecording = true
                        _speechState.value = Recording
                        eventBus.publish(SpeechStarted())
                    }
                    buffer.append(frame)
                } else {
                    if (isRecording) {
                        isRecording = false
                        _speechState.value = Transcribing
                        eventBus.publish(SpeechEnded())
                        
                        eventBus.publish(RecognitionStarted())
                        try {
                            val result = recognizer.transcribe(buffer)
                            eventBus.publish(SpeechRecognized(result.text))
                            eventBus.publish(ObservationCreated(
                                Observation(
                                    modality = Modality.AUDIO,
                                    source = Source.MICROPHONE,
                                    payload = SpeechRecognizedPayload(result.text, result.language),
                                    confidence = result.confidence,
                                    metadata = mapOf("engine" to "AudioPerceptionEngine")
                                )
                            ))
                            eventBus.publish(RecognitionFinished())
                        } catch (e: Exception) {
                            eventBus.publish(RecognitionFailed(e.message ?: "Unknown error"))
                        }
                        
                        buffer.clear()
                        _speechState.value = Listening
                    }
                }
            }
        }
    }

    override suspend fun stop() {
        job?.cancel()
        job = null
        audioSource.stop()
        _state.value = EngineState.Stopped
        _speechState.value = Idle
    }
}
