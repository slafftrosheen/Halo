package com.halo.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.halo.providers.android.tts.AndroidTTSProvider
import com.halo.sdk.provider.SpeechRequest
import com.halo.sdk.provider.SpeechResult
import com.halo.sdk.runtime.SpeechRouteEvidence
import com.halo.sdk.runtime.SpeechOutputTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicBoolean

sealed interface RoutedPlaybackResult {
    data class Completed(val durationMs: Long, val endpointId: Int) : RoutedPlaybackResult
    data object RouteLost : RoutedPlaybackResult
    data object Cancelled : RoutedPlaybackResult
    data class Failed(val reason: String) : RoutedPlaybackResult
}

data class AuthoritativeSpeechRouteLease(
    val target: SpeechOutputTarget.Wearable,
    val endpointId: Int,
    val endpointAddress: String,
    val acquiredAt: Long,
    val generation: Long,
)

/** Strict wearable playback: preferred-device requests are accepted only after routed-device proof. */
class RoutedSpeechPlaybackProvider(
    context: Context,
    private val ttsProvider: AndroidTTSProvider,
    private val audioAttributes: AudioAttributes
) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    @Volatile private var activeTrack: AudioTrack? = null

    suspend fun synthesize(
        request: SpeechRequest,
        evidence: SpeechRouteEvidence,
        onStarted: (() -> Unit)? = null,
        outputTarget: SpeechOutputTarget.Wearable? = null
    ): SpeechResult {
        return when (val res = synthesizeRouted(request, evidence, onStarted, outputTarget)) {
            is RoutedPlaybackResult.Completed -> SpeechResult(res.durationMs, true)
            else -> SpeechResult(0, false)
        }
    }

    suspend fun synthesizeRouted(
        request: SpeechRequest,
        evidence: SpeechRouteEvidence,
        onStarted: (() -> Unit)? = null,
        outputTarget: SpeechOutputTarget.Wearable? = null,
        generation: Long = 1L
    ): RoutedPlaybackResult {
        val startedAt = System.currentTimeMillis()
        val pcm = ttsProvider.synthesizeToPcm(request) ?: return RoutedPlaybackResult.Failed("PCM synthesis failed")
        val target = resolveTarget(evidence) ?: return RoutedPlaybackResult.Failed("Target device unresolvable")
        val acquiredAt = System.currentTimeMillis()
        val address = evidence.androidDeviceAddress ?: return RoutedPlaybackResult.Failed("Missing endpoint address")
        val lease = AuthoritativeSpeechRouteLease(
            target = outputTarget ?: SpeechOutputTarget.Wearable(address),
            endpointId = target.id,
            endpointAddress = address,
            acquiredAt = acquiredAt,
            generation = generation
        )

        val channelMask = when (pcm.channelCount) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> return RoutedPlaybackResult.Failed("Unsupported channel count")
        }
        val format = AudioFormat.Builder()
            .setSampleRate(pcm.sampleRate)
            .setEncoding(pcm.audioFormat)
            .setChannelMask(channelMask)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(pcm.sampleRate, channelMask, pcm.audioFormat)
        if (minBuffer <= 0) return RoutedPlaybackResult.Failed("Invalid min buffer size")
        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, 16 * 1024))
            .build()
        activeTrack = track

        val routeLost = AtomicBoolean(false)
        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                if (removedDevices?.any { it.id == lease.endpointId } == true) {
                    routeLost.set(true)
                }
            }
        }

        audioManager.registerAudioDeviceCallback(deviceCallback, null)

        return try {
            if (resolveTarget(evidence)?.id != lease.endpointId || !track.setPreferredDevice(target)) {
                return RoutedPlaybackResult.Failed("Failed to set preferred device")
            }
            track.setVolume(0f)
            track.play()
            val bytesPerSample = when (pcm.audioFormat) {
                AudioFormat.ENCODING_PCM_8BIT -> 1
                AudioFormat.ENCODING_PCM_FLOAT -> 4
                else -> 2
            }
            val probeSilence = ByteArray((pcm.sampleRate * pcm.channelCount * bytesPerSample / 50).coerceAtLeast(1))
            if (track.write(probeSilence, 0, probeSilence.size, AudioTrack.WRITE_BLOCKING) < 0) {
                return RoutedPlaybackResult.Failed("Probe silence write failed")
            }
            if (!awaitVerifiedRoute(track, lease.endpointId, evidence)) {
                return RoutedPlaybackResult.Failed("Route verification timeout")
            }
            track.setVolume(1f)
            var started = false
            var totalBytesWritten = probeSilence.size.toLong()
            for (chunk in pcm.chunks) {
                currentCoroutineContext().ensureActive()
                if (routeLost.get() || !routeMatches(track.routedDevice, evidence)) {
                    return RoutedPlaybackResult.RouteLost
                }
                var offset = 0
                while (offset < chunk.size) {
                    val written = track.write(chunk, offset, chunk.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) return RoutedPlaybackResult.Failed("Track write failed")
                    offset += written
                    totalBytesWritten += written
                    if (!started && track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                        routeMatches(track.routedDevice, evidence)) {
                        started = true
                        onStarted?.invoke()
                    }
                }
            }
            if (!started) return RoutedPlaybackResult.Failed("Playback failed to start")
            val expectedFrames = totalBytesWritten / (bytesPerSample * pcm.channelCount)
            while (track.playbackHeadPosition.toLong() < expectedFrames) {
                currentCoroutineContext().ensureActive()
                if (routeLost.get() || !routeMatches(track.routedDevice, evidence)) {
                    return RoutedPlaybackResult.RouteLost
                }
                delay(10L)
            }
            RoutedPlaybackResult.Completed(System.currentTimeMillis() - startedAt, lease.endpointId)
        } catch (_: CancellationException) {
            RoutedPlaybackResult.Cancelled
        } catch (e: Exception) {
            RoutedPlaybackResult.Failed(e.message ?: "Playback error")
        } finally {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            if (activeTrack === track) activeTrack = null
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

    private suspend fun awaitVerifiedRoute(track: AudioTrack, expectedDeviceId: Int, evidence: SpeechRouteEvidence): Boolean {
        repeat(30) {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                track.routedDevice?.id == expectedDeviceId && routeMatches(track.routedDevice, evidence)) return true
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
