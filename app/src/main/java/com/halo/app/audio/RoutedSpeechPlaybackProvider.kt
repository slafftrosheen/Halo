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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.takeWhile
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
    /** Default preserves older playback implementations; Android routed output overrides it. */
    suspend fun synthesizeRoutedRequests(initial: SpeechRequest, requests: Flow<SpeechRequest>,
        evidence: SpeechRouteEvidence, onStarted: (() -> Unit)? = null,
        outputTarget: SpeechOutputTarget? = null, generation: Long = 1L,
        acquireFocus: (() -> Boolean)? = null,
        provider: com.halo.sdk.provider.PcmSpeechSynthesisProvider,
        onRouteVerified: ((SpeechRouteEvidence) -> Unit)? = null): RoutedPlaybackResult {
        var result: RoutedPlaybackResult=RoutedPlaybackResult.Failed("No speech chunks")
        var route=evidence
        var started=false
        requests.takeWhile { result is RoutedPlaybackResult.Completed ||
            result is RoutedPlaybackResult.Failed && (result as RoutedPlaybackResult.Failed).reason=="No speech chunks" }
            .collect { chunk ->
                result=synthesizeRouted(chunk,route,{ if(!started) { started=true; onStarted?.invoke() } },
                    outputTarget,generation,acquireFocus,provider,{ actual ->
                        route=actual; onRouteVerified?.invoke(actual)
                    })
                (result as? RoutedPlaybackResult.Completed)?.evidence?.let { route=it }
            }
        return result
    }
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
        mutex.withLock {
            val started=SystemClock.elapsedRealtime()
            WorkloadSpeechTelemetry.playbackState.value=RoutedSpeechTiming(status="RUNNING")
            fun pcmReady() { if(WorkloadSpeechTelemetry.playbackState.value.firstPcmMs==null)
                WorkloadSpeechTelemetry.playbackState.value=WorkloadSpeechTelemetry.playbackState.value.copy(firstPcmMs=SystemClock.elapsedRealtime()-started) }
            val routedStarted: ()->Unit = {
                if(WorkloadSpeechTelemetry.playbackState.value.firstRoutedWriteMs==null)
                    WorkloadSpeechTelemetry.playbackState.value=WorkloadSpeechTelemetry.playbackState.value.copy(firstRoutedWriteMs=SystemClock.elapsedRealtime()-started)
                onStarted?.invoke()
            }
            try {
                // One response owns one AudioTrack and one verified route. Segment producers
                // are collected lazily, so later synthesis can overlap queued audio playback.
                val segmented = provider as? com.halo.sdk.provider.SegmentedPcmSpeechProvider
                val pinned = segmented?.pinSpeechRequest(request) ?: request
                // Keep at most one finished PCM segment ahead of playback. This starts
                // the next native synthesis while the preceding audio is still
                // draining, without unbounded buffering on memory-limited phones.
                val pcm = segmented?.streamPcm(pinned)?.buffer(1) ?: flow {
                    provider.synthesizeToPcm(pinned)?.let { emit(it) }
                }
                val result = playPcmStream(pinned, evidence, routedStarted, acquireFocus,
                    pcm, segmented != null, { actual -> onRouteVerified?.invoke(actual) }, ::pcmReady)
                WorkloadSpeechTelemetry.playbackState.value=WorkloadSpeechTelemetry.playbackState.value.copy(
                    completionMs=SystemClock.elapsedRealtime()-started,status=when(result) {
                        is RoutedPlaybackResult.Completed->"COMPLETED"
                        is RoutedPlaybackResult.Cancelled->"CANCELLED"
                        is RoutedPlaybackResult.RouteLost->"ROUTE_LOST"
                        is RoutedPlaybackResult.Failed->"FAILED"
                    })
                result
            } catch(cancelled: CancellationException) {
                WorkloadSpeechTelemetry.playbackState.value=WorkloadSpeechTelemetry.playbackState.value.copy(status="CANCELLED",completionMs=SystemClock.elapsedRealtime()-started)
                throw cancelled
            } catch(error: Exception) {
                WorkloadSpeechTelemetry.playbackState.value=WorkloadSpeechTelemetry.playbackState.value.copy(status="FAILED",completionMs=SystemClock.elapsedRealtime()-started)
                throw error
            }
        }
    }

    override suspend fun synthesizeRoutedRequests(initial: SpeechRequest, requests: Flow<SpeechRequest>,
        evidence: SpeechRouteEvidence, onStarted: (() -> Unit)?,
        outputTarget: SpeechOutputTarget?, generation: Long, acquireFocus: (() -> Boolean)?,
        provider: com.halo.sdk.provider.PcmSpeechSynthesisProvider,
        onRouteVerified: ((SpeechRouteEvidence) -> Unit)?): RoutedPlaybackResult =
        withContext(Dispatchers.IO) { mutex.withLock {
            val startedAt=SystemClock.elapsedRealtime()
            WorkloadSpeechTelemetry.playbackState.value=RoutedSpeechTiming(status="RUNNING")
            fun pcmReady() {
                val last=WorkloadSpeechTelemetry.playbackState.value
                if(last.firstPcmMs==null) WorkloadSpeechTelemetry.playbackState.value=
                    last.copy(firstPcmMs=SystemClock.elapsedRealtime()-startedAt)
            }
            val startedOnce=AtomicBoolean(false)
            val routedStart: () -> Unit = {
                if(startedOnce.compareAndSet(false,true)) {
                    val last=WorkloadSpeechTelemetry.playbackState.value
                    WorkloadSpeechTelemetry.playbackState.value=
                        last.copy(firstRoutedWriteMs=SystemClock.elapsedRealtime()-startedAt)
                    onStarted?.invoke()
                }
            }
            try {
                // Response chunks all share the same pinned provider/model/route;
                // only the sanitized visible text differs between chunks.
                val segmented=provider as? com.halo.sdk.provider.SegmentedPcmSpeechProvider
                val audio=flow {
                    requests.collect { chunk ->
                        currentCoroutineContext().ensureActive()
                        val pinned=segmented?.pinSpeechRequest(chunk) ?: chunk
                        if(segmented != null) segmented.streamPcm(pinned).collect { emit(it) }
                        else provider.synthesizeToPcm(pinned)?.let { emit(it) }
                    }
                }.buffer(1)
                val result=playPcmStream(initial,evidence,routedStart,acquireFocus,audio,true,
                    onRouteVerified,::pcmReady)
                WorkloadSpeechTelemetry.playbackState.value=
                    WorkloadSpeechTelemetry.playbackState.value.copy(
                        completionMs=SystemClock.elapsedRealtime()-startedAt,
                        status=when(result) {
                            is RoutedPlaybackResult.Completed->"COMPLETED"
                            is RoutedPlaybackResult.Cancelled->"CANCELLED"
                            is RoutedPlaybackResult.RouteLost->"ROUTE_LOST"
                            is RoutedPlaybackResult.Failed->"FAILED"
                        })
                result
            } catch(cancelled: CancellationException) {
                WorkloadSpeechTelemetry.playbackState.value=
                    WorkloadSpeechTelemetry.playbackState.value.copy(
                        status="CANCELLED",completionMs=SystemClock.elapsedRealtime()-startedAt)
                throw cancelled
            } catch(error: Exception) {
                WorkloadSpeechTelemetry.playbackState.value=
                    WorkloadSpeechTelemetry.playbackState.value.copy(
                        status="FAILED",completionMs=SystemClock.elapsedRealtime()-startedAt)
                throw error
            }
        } }

    private class PlaybackAbort(val result: RoutedPlaybackResult) : RuntimeException()

    /**
     * Keep one muted-until-verified route and AudioTrack for an entire streamed response.
     * Each PCM segment is consumed with backpressure; no unbounded concatenation or
     * re-opening the output endpoint between sentences. HALO still owns audio focus.
     */
    private suspend fun playPcmStream(
        request: SpeechRequest,
        evidence: SpeechRouteEvidence,
        onStarted: (() -> Unit)?,
        acquireFocus: (() -> Boolean)?,
        segments: Flow<com.halo.sdk.provider.PcmSpeech>,
        segmented: Boolean,
        onRouteVerified: ((SpeechRouteEvidence) -> Unit)?,
        onPcmReady: () -> Unit,
    ): RoutedPlaybackResult {
        val startedAt = SystemClock.elapsedRealtime()
        val systemDefault = evidence.mode == com.halo.sdk.runtime.SpeechRouteMode.SYSTEM_DEFAULT
        val target = if (systemDefault) null else resolveTarget(evidence)
        if (!systemDefault && target == null) return RoutedPlaybackResult.Failed("Selected audio device is unavailable")
        val pinned = java.util.concurrent.atomic.AtomicReference(evidence)
        var track: AudioTrack? = null
        val stopped = AtomicBoolean(false)
        val routeLost = AtomicBoolean(false)
        val verified = AtomicBoolean(false)
        var listenerRegistered = false
        var listener: AudioRouting.OnRoutingChangedListener? = null
        var expectedFrames = 0L
        var silenceFrames = 0L
        var frameBytes = 0
        var audioRate = 0
        var channels = 0
        var encoding: com.halo.sdk.provider.PcmEncoding? = null
        var started = false
        var received = false
        val playbackRanges = mutableListOf<com.halo.sdk.provider.SpeechPcmRange>()
        var rangeIndex = 0

        fun abort(result: RoutedPlaybackResult): Nothing = throw PlaybackAbort(result)
        fun requireRoute(device: AudioDeviceInfo?): Boolean = routeMatches(device, pinned.get())
        fun reportRanges(current: AudioTrack) {
            if (!verified.get() || stopped.get() || routeLost.get()) return
            val frame = (current.playbackHeadPosition.toLong() and 0xffffffffL) - silenceFrames
            while (rangeIndex < playbackRanges.size && playbackRanges[rangeIndex].frame <= frame) {
                val range = playbackRanges[rangeIndex++]
                request.onPlaybackRange?.invoke(range.start, range.end)
            }
        }
        suspend fun write(current: AudioTrack, bytes: ByteArray) {
            var offset = 0
            var lastProgress = SystemClock.elapsedRealtime()
            while (offset < bytes.size) {
                currentCoroutineContext().ensureActive()
                if (stopped.get()) abort(RoutedPlaybackResult.Cancelled)
                if (routeLost.get() || (verified.get() && !requireRoute(current.routedDevice)))
                    abort(RoutedPlaybackResult.RouteLost)
                reportRanges(current)
                val written = current.write(bytes, offset, bytes.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                if (written < 0) abort(RoutedPlaybackResult.Failed("Audio write failed: $written"))
                if (written == 0) {
                    if (SystemClock.elapsedRealtime() - lastProgress >= 3_000L)
                        abort(RoutedPlaybackResult.Failed("Audio playback stalled"))
                    delay(10)
                } else {
                    offset += written
                    lastProgress = SystemClock.elapsedRealtime()
                }
            }
        }

        try {
            segments.collect { pcm ->
                currentCoroutineContext().ensureActive()
                if (stopped.get()) abort(RoutedPlaybackResult.Cancelled)
                if (routeLost.get()) abort(RoutedPlaybackResult.RouteLost)
                if (!received) {
                    received = true
                    onPcmReady()
                    audioRate = pcm.sampleRateHz
                    channels = pcm.channelCount
                    encoding = pcm.encoding
                    val format = when (pcm.encoding) {
                        com.halo.sdk.provider.PcmEncoding.UNSIGNED_8 -> AudioFormat.ENCODING_PCM_8BIT
                        com.halo.sdk.provider.PcmEncoding.SIGNED_16_LE -> AudioFormat.ENCODING_PCM_16BIT
                        com.halo.sdk.provider.PcmEncoding.FLOAT_32_LE -> AudioFormat.ENCODING_PCM_FLOAT
                    }
                    val channelMask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                    frameBytes = pcm.encoding.bytesPerSample * channels
                    val minBuffer = AudioTrack.getMinBufferSize(audioRate, channelMask, format)
                    if (minBuffer <= 0) abort(RoutedPlaybackResult.Failed("Invalid audio buffer size"))
                    val current = AudioTrack.Builder().setAudioAttributes(audioAttributes)
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(audioRate)
                            .setEncoding(format).setChannelMask(channelMask).build())
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(maxOf(minBuffer, 16 * 1024)).build()
                    track = current
                    synchronized(trackLock) { activeTrack = current; activeStop = stopped }
                    if (current.state != AudioTrack.STATE_INITIALIZED || (target != null && !current.setPreferredDevice(target)))
                        abort(RoutedPlaybackResult.Failed("Unable to select audio device"))
                    listener = AudioRouting.OnRoutingChangedListener { routing ->
                        if (verified.get() && !requireRoute(routing.routedDevice)) {
                            routeLost.set(true)
                            synchronized(trackLock) {
                                if (activeTrack === current) {
                                    runCatching { current.setVolume(0f) }
                                    runCatching { current.pause() }
                                }
                            }
                        }
                    }
                    current.addOnRoutingChangedListener(requireNotNull(listener), Handler(Looper.getMainLooper()))
                    listenerRegistered = true
                    if (acquireFocus?.invoke() == false)
                        abort(RoutedPlaybackResult.Failed("Android denied audio focus; open Halo and try again"))
                    current.setVolume(0f)
                    current.play()
                    val silence = ByteArray((audioRate / 50) * frameBytes).apply {
                        if (format == AudioFormat.ENCODING_PCM_8BIT) fill(128.toByte())
                    }
                    silenceFrames = silence.size / frameBytes.toLong()
                    write(current, silence)
                    expectedFrames += silenceFrames
                    val routeReady = withTimeoutOrNull(1_500L) {
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val routed = current.routedDevice
                            if (systemDefault && routed != null) {
                                pinned.set(evidence.copy(androidDeviceId = routed.id,
                                    androidDeviceType = routed.type,
                                    androidDeviceName = routed.productName.toString(),
                                    mode = com.halo.sdk.runtime.SpeechRouteMode.EXACT_DEVICE,
                                    androidDeviceAddress = if (Build.VERSION.SDK_INT >= 28)
                                        routed.address.takeIf { it.isNotBlank() } else null))
                                if (!current.setPreferredDevice(routed)) return@withTimeoutOrNull false
                                break
                            }
                            if (!systemDefault && requireRoute(routed)) break
                            delay(20)
                        }
                        true
                    } == true
                    if (!routeReady || stopped.get())
                        abort(RoutedPlaybackResult.Failed("Audio route verification failed"))
                    verified.set(true)
                    synchronized(trackLock) {
                        if (routeLost.get() || !requireRoute(current.routedDevice))
                            abort(RoutedPlaybackResult.RouteLost)
                        onRouteVerified?.invoke(pinned.get())
                        current.setVolume(1f)
                    }
                    val endpointKey = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(pinned.get().androidDeviceId.toString().toByteArray()).take(6)
                        .joinToString("") { "%02x".format(it) }
                    android.util.Log.i("HaloAudio",
                        "HALO_AUDIO_ROUTE_VERIFIED mode=${evidence.mode} type=${pinned.get().androidDeviceType} endpointKey=$endpointKey")
                } else if (pcm.sampleRateHz != audioRate || pcm.channelCount != channels || pcm.encoding != encoding) {
                    abort(RoutedPlaybackResult.Failed("Speech segment format changed during playback"))
                }

                val current = requireNotNull(track)
                // Model-produced ranges in separate text segments have segment-local
                // offsets. Until the segmenter supplies absolute text offsets, do not
                // misreport those ranges as full-response highlighting positions.
                if (!segmented) {
                    val offsetFrames = expectedFrames - silenceFrames
                    pcm.ranges.filter { it.start >= 0 && it.end > it.start &&
                        it.end <= request.text.length && it.frame >= 0 }.forEach {
                        playbackRanges += it.copy(frame = it.frame + offsetFrames)
                    }
                }
                for (chunk in pcm.chunks) {
                    write(current, chunk)
                    expectedFrames += chunk.size / frameBytes.toLong()
                    if (!started && chunk.isNotEmpty()) {
                        started = true
                        onStarted?.invoke()
                    }
                }
            }
            val current = track ?: return RoutedPlaybackResult.Failed("No PCM audio returned")
            if (!started) return RoutedPlaybackResult.Failed("Speech contained no PCM samples")
            val deadline = SystemClock.elapsedRealtime() +
                expectedFrames * 1000L / audioRate + 3_000L
            while ((current.playbackHeadPosition.toLong() and 0xffffffffL) < expectedFrames) {
                currentCoroutineContext().ensureActive()
                if (stopped.get()) return RoutedPlaybackResult.Cancelled
                if (routeLost.get() || !requireRoute(current.routedDevice)) return RoutedPlaybackResult.RouteLost
                if (SystemClock.elapsedRealtime() >= deadline)
                    return RoutedPlaybackResult.Failed("Playback completion timed out")
                reportRanges(current)
                delay(10)
            }
            reportRanges(current)
            return RoutedPlaybackResult.Completed(SystemClock.elapsedRealtime() - startedAt,
                pinned.get().androidDeviceId ?: current.routedDevice?.id ?: -1, pinned.get())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (abort: PlaybackAbort) {
            return abort.result
        } catch (error: Exception) {
            return when {
                stopped.get() -> RoutedPlaybackResult.Cancelled
                routeLost.get() -> RoutedPlaybackResult.RouteLost
                else -> RoutedPlaybackResult.Failed(error.message ?: "Audio playback failed")
            }
        } finally {
            val current = track
            if (current != null) {
                if (listenerRegistered) listener?.let { runCatching { current.removeOnRoutingChangedListener(it) } }
                synchronized(trackLock) {
                    if (activeTrack === current) { activeTrack = null; activeStop = null }
                    runCatching { current.setVolume(0f) }
                    runCatching { current.pause() }
                    runCatching { current.flush() }
                    runCatching { current.release() }
                }
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
