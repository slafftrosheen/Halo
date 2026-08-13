package com.halo.drivers.heycyan

import com.halo.sdk.driver.Driver
import com.halo.sdk.host.AudioFrame
import com.halo.sdk.host.AudioSource
import com.halo.sdk.host.AudioSourceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * HeyCyan passive audio source.
 *
 * Status: Live BLE Microphone = VERIFIED
 *
 * The HeyCyan smart glasses emit a continuous stream of real-time audio 
 * over BLE via the `BC 59` notification characteristic. The stream contains
 * raw Opus packets.
 *
 * This AudioSource captures the decoded BC59 frames as long as the voice session is active.
 */
class HeyCyanAudioSource(
    private val driver: com.halo.drivers.heycyan.RealHeyCyanDriver,
    private val voiceSession: HeyCyanVoiceSession = HeyCyanVoiceSession(driver)
) : AudioSource {
    private val decoder = com.halo.drivers.heycyan.codec.RawOpusAudioDecoder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(AudioSourceState.IDLE)
    override val state: StateFlow<AudioSourceState> = _state.asStateFlow()

    override val frames: Flow<AudioFrame> = decoder.frames

    private var sessionPacketCount = 0
    private var sessionStartTime = 0L

    init {
        scope.launch {
            voiceSession.state.collect { sessionState ->
                when (sessionState) {
                    HeyCyanVoiceSession.State.ACTIVE -> {
                        _state.value = AudioSourceState.ACTIVE
                        sessionPacketCount = 0
                        sessionStartTime = System.currentTimeMillis()
                        android.util.Log.i("HALO-AUDIO", "[HEY_CYAN] session=STARTED")
                    }
                    HeyCyanVoiceSession.State.ENDED -> {
                        _state.value = AudioSourceState.IDLE
                        val duration = System.currentTimeMillis() - sessionStartTime
                        android.util.Log.i("HALO-AUDIO", "[HEY_CYAN] session=ENDED packets=$sessionPacketCount durationMs=$duration")
                    }
                    else -> {
                        _state.value = AudioSourceState.IDLE
                    }
                }
            }
        }

        scope.launch {
            driver.heyCyanEvents
                .filterIsInstance<HeyCyanEvent.RawAudioFrame>()
                .collect { event ->
                    // Only process audio frames when the voice session is ACTIVE (BC 73 payload 03 trigger)
                    if (voiceSession.isActive) {
                        sessionPacketCount++
                        decoder.decodePacket(event.payload, System.nanoTime() / 1000)
                    } else {
                        android.util.Log.d("HALO-AUDIO", "[HEY_CYAN] Dropping BC59 packet: no active session")
                    }
                }
        }
    }

    override suspend fun start() {
        decoder.initialize()
    }

    override suspend fun stop() {
        decoder.release()
    }
}
