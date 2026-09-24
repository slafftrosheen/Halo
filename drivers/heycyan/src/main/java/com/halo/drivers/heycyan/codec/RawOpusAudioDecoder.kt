package com.halo.drivers.heycyan.codec

import android.media.MediaCodec
import android.media.MediaFormat
import com.halo.sdk.host.AudioFrame
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AudioDecodeStats(
    val packets: Long = 0, val decodedFrames: Long = 0, val droppedFrames: Long = 0,
    val decodeErrors: Long = 0, val sampleRate: Int = 48000, val channels: Int = 1,
    val failure: String? = null
)

/** One codec per capture. EOS is drained before release; output buffering is bounded. */
class RawOpusAudioDecoder {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var codec: MediaCodec? = null
    private var drainJob: Job? = null
    private var output = Channel<AudioFrame>(64)
    val frames: Flow<AudioFrame> get() = output.receiveAsFlow()
    private val _stats = MutableStateFlow(AudioDecodeStats())
    val stats: StateFlow<AudioDecodeStats> = _stats.asStateFlow()
    private var baseUs = -1L
    private var epochNanos = 0L
    private var samples = 0L
    private var eos = CompletableDeferred<Unit>()

    suspend fun initialize() = mutex.withLock {
        check(codec == null) { "Decoder already active" }
        _stats.value = AudioDecodeStats()
        output = Channel(64); eos = CompletableDeferred()
        baseUs = -1; samples = 0; epochNanos = System.nanoTime()
        var created: MediaCodec? = null
        try {
            created = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, 48000, 1)
            val header = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
                .put("OpusHead".toByteArray(Charsets.US_ASCII)).put(1.toByte()).put(1.toByte())
                .putShort(312.toShort()).putInt(48000).putShort(0.toShort()).put(0.toByte()).array()
            format.setByteBuffer("csd-0", ByteBuffer.wrap(header))
            format.setByteBuffer("csd-1", ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(6500000L).apply { flip() })
            format.setByteBuffer("csd-2", ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(80000000L).apply { flip() })
            created.configure(format, null, null, 0); created.start()
            codec = created
            val activeCodec = created
            drainJob = scope.launch { drain(activeCodec) }
        } catch (error: Exception) {
            runCatching { created?.release() }; codec = null
            fail("initialize", error); throw error
        }
    }

    suspend fun decodePacket(payload: ByteArray, timestampUs: Long) = mutex.withLock {
        val active = checkNotNull(codec) { "Decoder is not active" }
        _stats.update { it.copy(packets = it.packets + 1) }
        try {
            val index = active.dequeueInputBuffer(10_000)
            if (index < 0) { _stats.update { it.copy(droppedFrames = it.droppedFrames + 1) }; return@withLock }
            val buffer = checkNotNull(active.getInputBuffer(index))
            buffer.clear(); buffer.put(payload)
            if (baseUs < 0) baseUs = timestampUs
            active.queueInputBuffer(index, 0, payload.size, (timestampUs - baseUs).coerceAtLeast(0), 0)
        } catch (error: Exception) { fail("packet", error); throw error }
    }

    private suspend fun drain(active: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        try {
            while (currentCoroutineContext().isActive) {
                val index = active.dequeueOutputBuffer(info, 10_000)
                if (index >= 0) {
                    try {
                        if (info.size > 0) {
                            val buffer = checkNotNull(active.getOutputBuffer(index)).duplicate().order(ByteOrder.LITTLE_ENDIAN)
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            val pcm = ShortArray(info.size / 2); buffer.asShortBuffer().get(pcm)
                            val format = _stats.value
                            val frame = AudioFrame(pcm, format.sampleRate, format.channels,
                                epochNanos + samples * 1_000_000_000L / format.sampleRate)
                            samples += pcm.size / format.channels
                            val sent = output.trySend(frame).isSuccess
                            _stats.update { it.copy(decodedFrames = it.decodedFrames + 1,
                                droppedFrames = it.droppedFrames + if (sent) 0 else 1) }
                        }
                    } finally { active.releaseOutputBuffer(index, false) }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { eos.complete(Unit); break }
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val format = active.outputFormat
                    _stats.update { it.copy(sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)) }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) { fail("output", error); output.close(error); eos.completeExceptionally(error)
        } finally { output.close() }
    }

    /** Called while the consumer still runs, so the last decoded frames reach ASR. */
    suspend fun finish() = mutex.withLock {
        val active = codec ?: return@withLock
        try {
            withTimeout(1500) {
                var index: Int
                do { index = active.dequeueInputBuffer(10_000); if (index < 0) yield() } while (index < 0)
                active.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                eos.await()
                drainJob?.join()
            }
        } catch (error: Exception) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            fail("finish", error)
            // EOF is mandatory even when the codec never acknowledges EOS.
            // Otherwise the capture owner joins an open frame stream forever.
            drainJob?.cancelAndJoin()
            drainJob = null
            output.close(IllegalStateException("Wearable audio decoder did not finish", error))
            throw IllegalStateException("Wearable audio decoder did not finish", error)
        }
    }

    suspend fun release() = mutex.withLock {
        drainJob?.cancelAndJoin(); drainJob = null
        val active = codec; codec = null
        try { active?.stop() } catch (error: Exception) { fail("stop", error) }
        try { active?.release() } catch (error: Exception) { fail("release", error) }
        output.close()
    }

    private fun fail(stage: String, error: Exception) {
        _stats.update { it.copy(decodeErrors = it.decodeErrors + 1, failure = "$stage: ${error.message}") }
        android.util.Log.e("WEARABLE_AUDIO", "stage=$stage error=${error.javaClass.simpleName}", error)
    }
}
