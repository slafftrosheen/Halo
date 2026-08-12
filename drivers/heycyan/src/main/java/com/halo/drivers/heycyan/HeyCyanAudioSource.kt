package com.halo.drivers.heycyan

import com.halo.sdk.driver.Driver
import com.halo.sdk.host.AudioFrame
import com.halo.sdk.host.AudioSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * HeyCyan passive audio source.
 *
 * Status: Live BLE Microphone = STRUCTURAL_VALIDATION
 *
 * The HeyCyan smart glasses emit a continuous stream of real-time audio 
 * over BLE via the `BC 59` notification characteristic. The stream contains
 * Opus-compatible data (verified via Android MediaCodec producing PCM), but
 * the exact RFC 6716 packet framing is still under structural validation
 * due to proprietary `4B 41` framing signatures.
 *
 * What HeyCyan DOES support for audio:
 * - On-device audio recording (mode 0x04 start / 0x05 stop)
 * - Transfer recorded audio files via WiFi (.opus format)
 * - Playback of recorded audio after transfer
 * - **Passive live stream (`BC 59`)**: Trigger unknown, currently under analysis.
 *
 * This AudioSource is currently PASSIVE. It does not actively send a start/stop
 * command because the exact physical trigger or BLE command that initiates the
 * `BC 59` stream is unknown. It will consume decoded BC59 frames once the parser
 * proves the framing.
 *
 * Do not connect this source to the cognitive loop yet.
 */
class HeyCyanAudioSource(
    private val driver: com.halo.drivers.heycyan.RealHeyCyanDriver,
    private val voiceSession: HeyCyanVoiceSession = HeyCyanVoiceSession(driver)
) : AudioSource {
    private val decoder = com.halo.drivers.heycyan.codec.RawOpusAudioDecoder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val frames: Flow<AudioFrame> = decoder.frames

    init {
        scope.launch {
            driver.heyCyanEvents
                .filterIsInstance<HeyCyanEvent.RawAudioFrame>()
                .collect { event ->
                    // Only process audio frames when the voice session is ACTIVE (BC 73 payload 03 trigger)
                    if (voiceSession.isActive) {
                        decoder.decodePacket(event.payload, System.nanoTime() / 1000)
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
