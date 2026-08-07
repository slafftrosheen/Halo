package com.halo.host.android

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.halo.runtime.kernel.Process
import com.halo.runtime.kernel.ProcessState
import com.halo.runtime.kernel.bus.PerceptionBus
import com.halo.icl.ontology.Observation
import com.halo.icl.core.CognitiveId
import com.halo.icl.core.Confidence
import com.halo.icl.core.Provenance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import com.halo.engines.speech.EnergyVAD
import com.halo.engines.speech.AudioFrame

class AudioPerceptionProcess(
    private val perceptionBus: PerceptionBus
) : Process {
    override val id: String = "driver.android.audio"
    override val name: String = "Android Audio Driver"
    
    private val _state = MutableStateFlow(ProcessState.Created)
    override val state: StateFlow<ProcessState> = _state

    private val _metrics = MutableStateFlow(com.halo.sdk.runtime.EngineMetrics(
        latencyMs = null,
        uptime = java.time.Duration.ZERO,
        eventsProcessed = 0L,
        queueSize = 0,
        cpuUsage = 0.0
    ))
    override val metrics: StateFlow<com.halo.sdk.runtime.EngineMetrics> = _metrics

    override fun engineSnapshot(): com.halo.sdk.runtime.EngineSnapshot = com.halo.sdk.runtime.EngineSnapshot(
        id = id,
        state = com.halo.sdk.runtime.EngineState.Running,
        health = com.halo.sdk.runtime.EngineHealth.Healthy,
        metrics = _metrics.value,
        lastActivity = java.time.Instant.now()
    )

    private var audioRecord: AudioRecord? = null
    
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    @SuppressLint("MissingPermission")
    override suspend fun start(): Unit = coroutineScope {
        _state.value = ProcessState.Running
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
        } catch (e: SecurityException) {
            _state.value = ProcessState.Crashed
            return@coroutineScope
        } catch (e: Exception) {
            _state.value = ProcessState.Crashed
            return@coroutineScope
        }
        
        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            _state.value = ProcessState.Crashed
            return@coroutineScope
        }
        
        val vad = EnergyVAD(threshold = 1000)
        
        launch(Dispatchers.IO) {
            val audioBuffer = ShortArray(bufferSize)
            val accumulatedAudio = mutableListOf<Short>()
            var isSpeaking = false
            var silenceFrames = 0
            val maxSilenceFrames = 5 // ~500ms depending on buffer size
            
            while (isActive && _state.value == ProcessState.Running) {
                val readResult = try {
                    audioRecord?.read(audioBuffer, 0, bufferSize) ?: -1
                } catch (e: Exception) {
                    -1
                }
                
                if (readResult > 0) {
                    val validData = audioBuffer.copyOf(readResult)
                    val frame = AudioFrame(validData, sampleRate, 1, System.nanoTime())
                    
                    if (vad.isSpeech(frame)) {
                        if (!isSpeaking) {
                            isSpeaking = true
                        }
                        accumulatedAudio.addAll(validData.toList())
                        silenceFrames = 0
                    } else {
                        if (isSpeaking) {
                            silenceFrames++
                            accumulatedAudio.addAll(validData.toList())
                            
                            if (silenceFrames > maxSilenceFrames) {
                                // Speech ended, emit observation
                                val finalAudio = accumulatedAudio.toShortArray()
                                val observation = Observation(
                                    id = CognitiveId(),
                                    created = Clock.System.now(),
                                    confidence = Confidence(1.0f),
                                    provenance = Provenance(id, null),
                                    edges = emptyList(),
                                    rawData = finalAudio, // PCM data representing one utterance
                                    modality = "AudioStream"
                                )
                                
                                perceptionBus.publish(id, observation)
                                
                                accumulatedAudio.clear()
                                isSpeaking = false
                                silenceFrames = 0
                            }
                        }
                    }
                } else {
                    delay(10)
                }
            }
        }
    }

    override suspend fun pause() {
        _state.value = ProcessState.Paused
        audioRecord?.stop()
    }
    
    override suspend fun resume() {
        _state.value = ProcessState.Running
        audioRecord?.startRecording()
    }
    
    override suspend fun stop() {
        _state.value = ProcessState.Stopped
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
