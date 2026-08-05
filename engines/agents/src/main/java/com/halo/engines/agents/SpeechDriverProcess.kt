package com.halo.engines.agents

import com.halo.runtime.kernel.Process
import com.halo.runtime.kernel.ProcessState
import com.halo.runtime.kernel.bus.PerceptionBus
import com.halo.icl.ontology.Observation
import com.halo.icl.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock

class SpeechDriverProcess(
    private val perceptionBus: PerceptionBus
) : Process {
    override val id: String = "driver.speech.heycyan"
    override val name: String = "Speech Driver"
    
    private val _state = MutableStateFlow(ProcessState.Created)
    override val state: StateFlow<ProcessState> = _state

    override suspend fun start() {
        _state.value = ProcessState.Running
        // In reality, connects to microphone, runs STT, publishes Observation
        
        val observation = Observation(
            id = CognitiveId(),
            created = Clock.System.now(),
            confidence = Confidence(0.95f),
            provenance = Provenance(id, null),
            edges = emptyList(),
            rawData = "Play some music",
            modality = "Speech"
        )
        
        perceptionBus.publish(id, observation)
    }

    override suspend fun pause() { _state.value = ProcessState.Paused }
    override suspend fun resume() { _state.value = ProcessState.Running }
    override suspend fun stop() { _state.value = ProcessState.Stopped }
}
