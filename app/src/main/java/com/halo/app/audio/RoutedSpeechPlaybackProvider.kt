package com.halo.app.audio

import android.content.Context
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.halo.sdk.provider.SpeechRequest
import com.halo.sdk.provider.SpeechResult
import com.halo.sdk.runtime.SpeechRouteEvidence
import com.halo.sdk.runtime.SpeechOutputTarget
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

sealed interface RoutedPlaybackResult {
    data class Completed(val durationMs: Long, val endpointId: Int, val evidence: SpeechRouteEvidence? = null) : RoutedPlaybackResult
    data object RouteLost : RoutedPlaybackResult
    data object Cancelled : RoutedPlaybackResult
    data class Failed(val reason: String) : RoutedPlaybackResult
}

data class AuthoritativeSpeechRouteLease(
    val target: SpeechOutputTarget,
    val endpointId: Int,
    val endpointAddress: String?,
    val acquiredAt: Long,
    val generation: Long,
)

interface SpeechChunkPlayback {
    suspend fun synthesizeRouted(request: SpeechRequest, evidence: SpeechRouteEvidence,
        onStarted: (() -> Unit)? = null, outputTarget: SpeechOutputTarget? = null,
        generation: Long = 1L, acquireFocus: (() -> Boolean)? = null,
        provider: com.halo.sdk.provider.PcmSpeechSynthesisProvider, onRouteVerified: ((SpeechRouteEvidence) -> Unit)? = null): RoutedPlaybackResult
    fun stopCurrent()
}

/** Both phone and wearable output require actual AudioTrack route proof before unmuting. */
class RoutedSpeechPlaybackProvider(context: Context, private val ttsProvider: com.halo.sdk.provider.PcmSpeechSynthesisProvider,
    private val audioAttributes: AudioAttributes) : SpeechChunkPlayback {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val mutex = Mutex()
    private val trackLock = Any()
    private var activeTrack: AudioTrack? = null
    private var activeStop: AtomicBoolean? = null

    suspend fun synthesize(request: SpeechRequest, evidence: SpeechRouteEvidence,
        onStarted: (() -> Unit)? = null, outputTarget: SpeechOutputTarget? = null): SpeechResult =
        when (val result = synthesizeRouted(request, evidence, onStarted, outputTarget, provider = ttsProvider)) {
            is RoutedPlaybackResult.Completed -> SpeechResult(result.durationMs, true)
            else -> SpeechResult(0, false)
        }

    override suspend fun synthesizeRouted(request: SpeechRequest, evidence: SpeechRouteEvidence,
        onStarted: (() -> Unit)?, outputTarget: SpeechOutputTarget?,
        generation: Long, acquireFocus: (() -> Boolean)?,
        provider: com.halo.sdk.provider.PcmSpeechSynthesisProvider, onRouteVerified: ((SpeechRouteEvidence) -> Unit)?): RoutedPlaybackResult = withContext(Dispatchers.IO) {
        mutex.withLock { play(request, evidence, onStarted, acquireFocus, provider, onRouteVerified) }
    }

    private suspend fun play(request: SpeechRequest, evidence: SpeechRouteEvidence,
        onStarted: (() -> Unit)?, acquireFocus: (() -> Boolean)?, provider: com.halo.sdk.provider.PcmSpeechSynthesisProvider, onRouteVerified: ((SpeechRouteEvidence) -> Unit)?): RoutedPlaybackResult {
        val startedAt = SystemClock.elapsedRealtime()
        val pcm = provider.synthesizeToPcm(request) ?: return RoutedPlaybackResult.Failed("PCM synthesis failed or timed out")
        val audioFormat = when (pcm.encoding) {
            com.halo.sdk.provider.PcmEncoding.UNSIGNED_8 -> AudioFormat.ENCODING_PCM_8BIT
            com.halo.sdk.provider.PcmEncoding.SIGNED_16_LE -> AudioFormat.ENCODING_PCM_16BIT
            com.halo.sdk.provider.PcmEncoding.FLOAT_32_LE -> AudioFormat.ENCODING_PCM_FLOAT
        }
        currentCoroutineContext().ensureActive()
        val systemDefault = evidence.mode == com.halo.sdk.runtime.SpeechRouteMode.SYSTEM_DEFAULT
        val target = if (systemDefault) null else resolveTarget(evidence)
        if (!systemDefault && target == null) return RoutedPlaybackResult.Failed("Selected audio device is unavailable")
        val pinned = java.util.concurrent.atomic.AtomicReference(evidence)
        val channelMask = when (pcm.channelCount) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> return RoutedPlaybackResult.Failed("Unsupported channel count")
        }
        val bytesPerSample = when (audioFormat) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> return RoutedPlaybackResult.Failed("Unsupported PCM format")
        }
        val frameBytes = bytesPerSample * pcm.channelCount
        val minBuffer = AudioTrack.getMinBufferSize(pcm.sampleRateHz, channelMask, audioFormat)
        if (minBuffer <= 0) return RoutedPlaybackResult.Failed("Invalid audio buffer size")
        val track = AudioTrack.Builder().setAudioAttributes(audioAttributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(pcm.sampleRateHz)
                .setEncoding(audioFormat).setChannelMask(channelMask).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minBuffer, 16 * 1024)).build()
        val stopped = AtomicBoolean(false)
        val routeLost = AtomicBoolean(false)
        val verified = AtomicBoolean(false)
        synchronized(trackLock) { activeTrack = track; activeStop = stopped }
        val listener = AudioRouting.OnRoutingChangedListener { routing ->
            if (verified.get() && !routeMatches(routing.routedDevice, pinned.get())) {
                routeLost.set(true)
                synchronized(trackLock) {
                    if (activeTrack === track) { runCatching { track.setVolume(0f) }; runCatching { track.pause() } }
                }
            }
        }
        var listenerRegistered = false
        try {
            if (track.state != AudioTrack.STATE_INITIALIZED || (target != null && !track.setPreferredDevice(target)))
                return RoutedPlaybackResult.Failed("Unable to select audio device")
            track.addOnRoutingChangedListener(listener, Handler(Looper.getMainLooper()))
            listenerRegistered = true
            if (acquireFocus?.invoke() == false) return RoutedPlaybackResult.Failed("Android denied audio focus; open Halo and try again")
            track.setVolume(0f)
            track.play()
            val silence = ByteArray((pcm.sampleRateHz / 50) * frameBytes).apply {
                if (audioFormat == AudioFormat.ENCODING_PCM_8BIT) fill(128.toByte())
            }
            suspend fun write(bytes: ByteArray) {
                var offset = 0
                var lastProgress = SystemClock.elapsedRealtime()
                while (offset < bytes.size) {
                    currentCoroutineContext().ensureActive()
                    check(!stopped.get()) { "Playback stopped" }
                    if (verified.get() && !routeMatches(track.routedDevice, pinned.get())) routeLost.set(true)
                    check(!routeLost.get()) { "Audio route lost" }
                    val written = track.write(bytes, offset, bytes.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                    check(written >= 0) { "Audio write failed: $written" }
                    if (written == 0) {
                        check(SystemClock.elapsedRealtime() - lastProgress < 3_000L) { "Audio playback stalled" }
                        delay(10)
                    } else { offset += written; lastProgress = SystemClock.elapsedRealtime() }
                }
            }
            write(silence)
            val routeReady = withTimeoutOrNull(1_500L) {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val routed = track.routedDevice
                    if (systemDefault && routed != null) {
                        pinned.set(evidence.copy(androidDeviceId = routed.id, androidDeviceType = routed.type, androidDeviceName = routed.productName.toString(),
                            mode = com.halo.sdk.runtime.SpeechRouteMode.EXACT_DEVICE,
                            androidDeviceAddress = if (Build.VERSION.SDK_INT >= 28) routed.address.takeIf { it.isNotBlank() } else null))
                        if (!track.setPreferredDevice(routed)) return@withTimeoutOrNull false
                        break
                    }
                    if (!systemDefault && routeMatches(routed, pinned.get())) break
                    delay(20)
                }
                true
            } == true
            if (!routeReady || stopped.get()) return RoutedPlaybackResult.Failed("Audio route verification failed")
            verified.set(true)
            if (!routeMatches(track.routedDevice, pinned.get())) return RoutedPlaybackResult.RouteLost
            synchronized(trackLock) {
                if (routeLost.get() || !routeMatches(track.routedDevice, pinned.get())) return RoutedPlaybackResult.RouteLost
                onRouteVerified?.invoke(pinned.get())
                track.setVolume(1f)
            }
            val endpointKey = java.security.MessageDigest.getInstance("SHA-256")
                .digest(pinned.get().androidDeviceId.toString().toByteArray()).take(6).joinToString("") { "%02x".format(it) }
            android.util.Log.i("HaloAudio", "HALO_AUDIO_ROUTE_VERIFIED mode=${evidence.mode} type=${pinned.get().androidDeviceType} endpointKey=$endpointKey")
            var total = silence.size.toLong()
            var started = false
            for (chunk in pcm.chunks) {
                write(chunk)
                total += chunk.size
                if (!started) { started = true; onStarted?.invoke() }
            }
            val expectedFrames = total / frameBytes
            val deadline = SystemClock.elapsedRealtime() + expectedFrames * 1000L / pcm.sampleRateHz + 3_000L
            while ((track.playbackHeadPosition.toLong() and 0xffffffffL) < expectedFrames) {
                currentCoroutineContext().ensureActive()
                if (stopped.get()) return RoutedPlaybackResult.Cancelled
                if (routeLost.get() || !routeMatches(track.routedDevice, pinned.get())) return RoutedPlaybackResult.RouteLost
                if (SystemClock.elapsedRealtime() >= deadline) return RoutedPlaybackResult.Failed("Playback completion timed out")
                delay(10)
            }
            return RoutedPlaybackResult.Completed(SystemClock.elapsedRealtime() - startedAt, pinned.get().androidDeviceId ?: track.routedDevice.id, pinned.get())
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            return when {
                stopped.get() -> RoutedPlaybackResult.Cancelled
                routeLost.get() -> RoutedPlaybackResult.RouteLost
                else -> RoutedPlaybackResult.Failed(error.message ?: "Audio playback failed")
            }
        } finally {
            if (listenerRegistered) runCatching { track.removeOnRoutingChangedListener(listener) }
            synchronized(trackLock) {
                if (activeTrack === track) { activeTrack = null; activeStop = null }
                runCatching { track.setVolume(0f) }; runCatching { track.pause() }; runCatching { track.flush() }; runCatching { track.release() }
            }
        }
    }

    override fun stopCurrent(): Unit = synchronized(trackLock) {
        activeStop?.set(true)
        activeTrack?.let { track -> runCatching { track.setVolume(0f) }; runCatching { track.pause() }; runCatching { track.flush() } }
        Unit
    }
    private fun resolveTarget(evidence: SpeechRouteEvidence) = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { routeMatches(it, evidence) }
    private fun routeMatches(device: AudioDeviceInfo?, evidence: SpeechRouteEvidence): Boolean =
        device != null && com.halo.sdk.runtime.matchesSpeechRoute(evidence, device.id, device.type,
            if (Build.VERSION.SDK_INT >= 28) device.address else null)
}
