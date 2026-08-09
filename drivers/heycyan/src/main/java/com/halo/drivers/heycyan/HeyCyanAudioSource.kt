package com.halo.drivers.heycyan

import com.halo.sdk.driver.Driver
import com.halo.sdk.host.AudioFrame
import com.halo.sdk.host.AudioSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * HeyCyan audio source — deliberately empty.
 *
 * Status: Live BLE Microphone = UNSUPPORTED_BY_DEVICE
 *
 * The HeyCyan smart glasses do NOT support live audio streaming over BLE.
 * There is no PCM, codec, or audio data path available through the BLE
 * protocol. This has been confirmed through:
 *
 * 1. SDK analysis: No audio streaming API exists in the HeyCyan SDK
 * 2. CyanBridge forensics: Audio is recorded on-device, not streamed
 * 3. Protocol analysis: GATT characteristics carry only command/event frames
 *
 * What HeyCyan DOES support for audio:
 * - On-device audio recording (mode 0x04 start / 0x05 stop)
 * - Transfer recorded audio files via WiFi (.opus format)
 * - Playback of recorded audio after transfer
 *
 * For the Halo cognitive loop, use PhoneMicrophoneAudioSource
 * (Android hardware microphone) as the live audio input source.
 * The cognitive pipeline is:
 *
 * ```
 * Phone Mic → PCM → VAD → Wake Word → Speech Recognition → Intent
 * ```
 *
 * HeyCyan glasses provide:
 * - Button events (single/double/long press) for interaction triggers
 * - On-device recording for later analysis
 * - Camera/video for visual context
 *
 * Do NOT attempt to fake a live audio capability from this source.
 * See: HEYCYAN_FUNCTIONALITY_MATRIX.md
 */
class HeyCyanAudioSource(private val driver: Driver) : AudioSource {
    override val frames: Flow<AudioFrame> = emptyFlow()

    override suspend fun start() = Unit

    override suspend fun stop() = Unit
}
