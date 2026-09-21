package com.halo.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.halo.providers.android.tts.AndroidTTSProvider
import com.halo.sdk.provider.SpeechRequest
import com.halo.sdk.provider.SpeechResult
import com.halo.sdk.runtime.SpeechRouteEvidence
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Strict wearable playback: preferred-device requests are accepted only after routed-device proof. */
class RoutedSpeechPlaybackProvider(
    context: Context,
    private val ttsProvider: AndroidTTSProvider,
    private val audioAttributes: android.media.AudioAttributes
) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    @Volatile private var activeTrack: AudioTrack? = null

    suspend fun synthesize(
        request: SpeechRequest,
        evidence: SpeechRouteEvidence,
        onStarted: (() -> Unit)? = null
    ): SpeechResult {
        val startedAt = System.currentTimeMillis()
        val pcm = ttsProvider.synthesizeToPcm(request) ?: return SpeechResult(0, false)
        val target = resolveTarget(evidence) ?: return SpeechResult(0, false)
        val channelMask = when (pcm.channelCount) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> return SpeechResult(0, false)
        }
        val format = AudioFormat.Builder()
            .setSampleRate(pcm.sampleRate)
            .setEncoding(pcm.audioFormat)
            .setChannelMask(channelMask)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(pcm.sampleRate, channelMask, pcm.audioFormat)
        if (minBuffer <= 0) return SpeechResult(0, false)
        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, 16 * 1024))
            .build()
        activeTrack = track
        return try {
            if (!track.setPreferredDevice(target)) return SpeechResult(0, false)
            track.setVolume(0f)
            track.play()
            val bytesPerSample = when (pcm.audioFormat) {
                AudioFormat.ENCODING_PCM_8BIT -> 1
                AudioFormat.ENCODING_PCM_FLOAT -> 4
                else -> 2
            }
            val probeSilence = ByteArray((pcm.sampleRate * pcm.channelCount * bytesPerSample / 50).coerceAtLeast(1))
            if (track.write(probeSilence, 0, probeSilence.size, AudioTrack.WRITE_BLOCKING) < 0) {
                return SpeechResult(0, false)
            }
            if (!awaitVerifiedRoute(track, evidence)) return SpeechResult(0, false)
            track.setVolume(1f)
            onStarted?.invoke()
            var totalBytesWritten = probeSilence.size.toLong()
            for (chunk in pcm.chunks) {
                currentCoroutineContext().ensureActive()
                if (!routeMatches(track.routedDevice, evidence)) return SpeechResult(0, false)
                var offset = 0
                while (offset < chunk.size) {
                    val written = track.write(chunk, offset, chunk.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) return SpeechResult(0, false)
                    offset += written
                    totalBytesWritten += written
                }
            }
            val expectedFrames = totalBytesWritten / (bytesPerSample * pcm.channelCount)
            while (track.playbackHeadPosition.toLong() < expectedFrames) {
                currentCoroutineContext().ensureActive()
                if (!routeMatches(track.routedDevice, evidence)) return SpeechResult(0, false)
                delay(10L)
            }
            SpeechResult(System.currentTimeMillis() - startedAt, true)
        } finally {
            activeTrack = null
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            track.release()
        }
    }

    fun stopCurrent() {
        activeTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
        }
    }

    private suspend fun awaitVerifiedRoute(track: AudioTrack, evidence: SpeechRouteEvidence): Boolean {
        repeat(20) {
            if (routeMatches(track.routedDevice, evidence)) return true
            delay(25L)
        }
        return false
    }

    private fun resolveTarget(evidence: SpeechRouteEvidence): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { routeMatches(it, evidence) }
    }

    private fun routeMatches(device: AudioDeviceInfo?, evidence: SpeechRouteEvidence): Boolean {
        if (device == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val expected = evidence.androidDeviceAddress ?: return false
        return device.address.equals(expected, ignoreCase = true)
    }
}
