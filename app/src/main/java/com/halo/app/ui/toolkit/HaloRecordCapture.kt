package com.halo.app.ui.toolkit

import android.content.Context
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.nativeactions.EverydayWidgetRecording
import com.halo.host.android.audio.AndroidAudioSource
import com.halo.providers.sherpa.stt.ReloadableSpeechRecognitionProvider
import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.host.AudioFrame
import com.halo.sdk.runtime.resource.*
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.takeWhile

internal data class HaloRecordCaption(val startMs: Int, val endMs: Int, val text: String, val speaker: Char? = null) {
    val elapsedSeconds: Int get() = startMs / 1000
}
internal data class HaloRecordDraft(val file: File, val elapsedSeconds: Int, val interrupted: Boolean = false)

private const val sampleRate = 16_000
private const val clipSamples = sampleRate * 5
internal const val RECORD_MAX_SECONDS = 30 * 60
private const val maxSamples = sampleRate * RECORD_MAX_SECONDS

/** A foreground recording owns the same phone microphone lease as the voice interaction. */
internal class HaloRecordCapture(private val context: Context) {
    private val stopRequested = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val activeSamples = AtomicInteger()
    private var captionJob: Job? = null
    @Volatile private var claim: String? = null
    fun owns(token: String) = claim == token
    fun setPaused(value: Boolean) { paused.set(value) }
    fun recordedSeconds(): Int = activeSamples.get() / sampleRate
    fun stop() {
        stopRequested.set(true)
        claim?.let { HaloRecordSessions.stop(it) }
    }
    fun closeCaptions() { captionJob?.cancel() }

    suspend fun capture(
        captionScope: CoroutineScope,
        captionsEnabled: Boolean,
        onStarted: () -> Unit,
        onCaption: (HaloRecordCaption) -> Unit,
        onCaptionStatus: (Boolean) -> Unit,
        onSpeakerStatus: (Boolean) -> Unit,
        allowed: () -> Boolean,
    ): HaloRecordDraft? {
        check(allowed())
        val reviewed = HaloRecordSessions.begin { stopRequested.set(true) }
        claim = reviewed.sessionToken
        return try {
            captureOwned(captionScope, captionsEnabled, onStarted, onCaption, onCaptionStatus, onSpeakerStatus, allowed, reviewed)
        } finally {
            HaloRecordSessions.finish(reviewed.sessionToken)
            claim = null
        }
    }

    private suspend fun captureOwned(
        captionScope: CoroutineScope,
        captionsEnabled: Boolean,
        onStarted: () -> Unit,
        onCaption: (HaloRecordCaption) -> Unit,
        onCaptionStatus: (Boolean) -> Unit,
        onSpeakerStatus: (Boolean) -> Unit,
        allowed: () -> Boolean,
        reviewed: HaloRecordSessionState,
    ): HaloRecordDraft? {
        val id = "halo-record:${UUID.randomUUID()}"
        val granted = Graph.infra.resourceLeaseManager.tryAcquire(ResourceLeaseRequest(
            id, ResourceHolder(id), setOf(
                ResourceClaim(ResourceKey(HaloResourceIds.AudioInput, ResourceEndpointId("host:primary"))),
                ResourceClaim(ResourceKey(HaloResourceIds.AudioSession, ResourceEndpointId("host:android-audio"))),
            ), preemption = ResourcePreemptionPolicy.NEVER,
        ))
        check(granted is ResourceAcquireResult.Granted)
        val microphone = AndroidAudioSource(context)
        var recordedSamples = 0
        val captionSamples = ShortArray(clipSamples)
        var captionCount = 0
        var captionOffset = 0
        val batches = Channel<Pair<Int, ShortArray>>(capacity = 16, onUndeliveredElement = { it.second.fill(0) })
        val captions = if (captionsEnabled) captionScope.launch {
            val model = Graph.providers.speechRecognition as? ReloadableSpeechRecognitionProvider
            var speakers: HaloLocalSpeakerLabels? = null
            try {
                val ready = model != null && model.metadata.local && model.health()
                onCaptionStatus(ready)
                if (model == null || !ready || !allowed()) return@launch
                speakers = HaloRecordSpeakerModels.open(context)
                onSpeakerStatus(speakers != null)
                for ((offset, samples) in batches) {
                    try {
                        if (!allowed()) break
                        val spans = try { speakers?.spans(samples) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) {
                                speakers?.close()
                                speakers = null
                                onSpeakerStatus(false)
                                null
                            } ?: listOf(HaloRecordSpeechSpan(0, samples.size))
                        for (span in spans) {
                            if (!allowed()) break
                            val position = offset + span.start
                            val clip = samples.copyOfRange(span.start, span.end)
                            try {
                                val result = model.recognizeLocal(AudioBuffer(listOf(AudioFrame(
                                    clip, sampleRate, 1, position * 1_000_000_000L / sampleRate)), "$id:$position"))
                                val line = result.text.trim()
                                if (line.isNotEmpty() && allowed()) onCaption(HaloRecordCaption(
                                    startMs = (position * 1000L / sampleRate).toInt(),
                                    endMs = (((position + clip.size) * 1000L + sampleRate - 1) / sampleRate).toInt(),
                                    text = line, speaker = span.speaker,
                                ))
                            } finally { clip.fill(0) }
                        }
                    } finally { samples.fill(0) }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { onCaptionStatus(false) }
            finally {
                try { withContext(NonCancellable + Dispatchers.Default) { speakers?.close() } }
                finally { batches.cancel() }
            }
        } else null
        captionJob = captions
        var routeLost = false
        var staged: File? = null
        var keep = false
        try {
            if (stopRequested.get() || !allowed()) return null
            val destination = withContext(Dispatchers.IO) {
                val staging = File(context.filesDir, recordDraftFolder)
                check(staging.isDirectory || staging.mkdirs())
                val threshold = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                staging.listFiles().orEmpty().filter { it.name.startsWith("voice-") && it.lastModified() < threshold }
                    .forEach(File::delete)
                check(staging.usableSpace >= maxSamples * Short.SIZE_BYTES.toLong() + 1_000_000)
                File.createTempFile("voice-", ".wav", staging).also { staged = it }
            }
            if (stopRequested.get() || !allowed()) return null
            microphone.start()
            HaloRecordSessions.started(reviewed.sessionToken)
            EverydayWidgetRecording.active.value = true
            onStarted()
            withContext(Dispatchers.IO) {
                DataOutputStream(destination.outputStream().buffered()).use { output ->
                    writeRecordHeader(output, 0)
                    try {
                        withTimeoutOrNull(RECORD_MAX_SECONDS * 1000L) {
                            var needsStart = false
                            fun mayCapture() = !stopRequested.get() && allowed() && recordedSamples < maxSamples
                            while (mayCapture()) {
                                if (paused.get()) {
                                    if (!needsStart) { microphone.stop(); needsStart = true }
                                    delay(40)
                                    continue
                                }
                                if (needsStart) { microphone.start(); needsStart = false }
                                microphone.frames.flowOn(Dispatchers.IO).takeWhile {
                                    mayCapture() && !paused.get()
                                }.collect { frame ->
                                    check(frame.sampleRate == sampleRate && frame.channels == 1)
                                    if (paused.get()) {
                                        frame.samples.fill(0)
                                        return@collect
                                    }
                                    val count = frame.samples.size.coerceAtMost(maxSamples - recordedSamples)
                                    for (index in 0 until count) {
                                        val sample = frame.samples[index]
                                        output.writeShort(java.lang.Short.reverseBytes(sample).toInt())
                                        if (captions?.isActive == true) {
                                            if (captionCount == 0) captionOffset = recordedSamples
                                            captionSamples[captionCount++] = sample
                                            if (captionCount == clipSamples) {
                                                val clip = captionSamples.copyOf()
                                                if (batches.trySend(captionOffset to clip).isFailure) clip.fill(0)
                                                captionCount = 0
                                            }
                                        }
                                        recordedSamples++
                                    }
                                    activeSamples.set(recordedSamples)
                                }
                                if (mayCapture()) { microphone.stop(); needsStart = true }
                            }
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) {
                        if (recordedSamples < sampleRate / 4) throw error
                        routeLost = true
                    }
                }
                if (recordedSamples >= sampleRate / 4) {
                    val size = recordedSamples * Short.SIZE_BYTES
                    RandomAccessFile(destination, "rw").use { file ->
                        check(file.length() >= 44L + size)
                        file.seek(4)
                        file.writeInt(Integer.reverseBytes(36 + size))
                        file.seek(40)
                        file.writeInt(Integer.reverseBytes(size))
                    }
                }
            }
            if (recordedSamples < sampleRate / 4) return null
            keep = true
            return HaloRecordDraft(destination, (recordedSamples + sampleRate / 2) / sampleRate, routeLost)
        } finally {
            try { withContext(NonCancellable) { microphone.close() } }
            finally {
                EverydayWidgetRecording.active.value = false
                granted.lease.release()
                if (captionCount >= sampleRate / 4 && captions?.isActive == true) {
                    val clip = captionSamples.copyOf(captionCount)
                    if (batches.trySend(captionOffset to clip).isFailure) clip.fill(0)
                }
                captionSamples.fill(0)
                batches.close()
                if (!keep) withContext(NonCancellable + Dispatchers.IO) { staged?.delete() }
            }
        }
    }
}

internal const val recordDraftFolder = "workspace/owned/record-staging"

internal fun writeRecordHeader(output: DataOutputStream, size: Int) {
    output.writeBytes("RIFF")
    output.writeInt(Integer.reverseBytes(36 + size))
    output.writeBytes("WAVEfmt ")
    output.writeInt(Integer.reverseBytes(16))
    output.writeShort(java.lang.Short.reverseBytes(1).toInt())
    output.writeShort(java.lang.Short.reverseBytes(1).toInt())
    output.writeInt(Integer.reverseBytes(sampleRate))
    output.writeInt(Integer.reverseBytes(sampleRate * Short.SIZE_BYTES))
    output.writeShort(java.lang.Short.reverseBytes(2).toInt())
    output.writeShort(java.lang.Short.reverseBytes(16).toInt())
    output.writeBytes("data")
    output.writeInt(Integer.reverseBytes(size))
}
