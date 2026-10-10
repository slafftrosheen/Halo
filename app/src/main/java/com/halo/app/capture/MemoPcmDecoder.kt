package com.halo.app.capture

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.host.AudioFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DecodedMemo(val audio: AudioBuffer, val sourceSampleRate: Int, val sourceChannels: Int, val durationMs: Long)

/** Streaming PCM16 adaptation. Partial reads keep their remaining bytes across codec buffers. */
internal class MemoPcmAdapter(private val sampleRate: Int, private val channels: Int) {
    init { require(sampleRate in 8_000..96_000 && channels in 1..2) }
    private var remainder = byteArrayOf()
    private var inputIndex = -1L
    private var nextOutput = 0.0
    private var previous = 0.0
    private val samples = ShortArray(16_000 * 120)
    private var sampleCount = 0
    fun accept(bytes: ByteArray) {
        require(bytes.size <= 1_048_576) { "Audio buffer exceeds decode budget" }
        val combined = remainder + bytes
        val frameBytes = channels * 2
        val complete = combined.size / frameBytes * frameBytes
        val buffer = ByteBuffer.wrap(combined, 0, complete).order(ByteOrder.LITTLE_ENDIAN)
        while (buffer.remaining() >= frameBytes) {
            var mono = 0.0
            repeat(channels) { mono += buffer.short.toDouble() / channels }
            inputIndex++
            while (nextOutput <= inputIndex) {
                val interpolated = if (inputIndex == 0L) mono else previous + (mono - previous) * (nextOutput - (inputIndex - 1))
                check(sampleCount < samples.size) { "Recording exceeds two minutes. Split the recording." }
                samples[sampleCount++] = interpolated.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                nextOutput += sampleRate / 16_000.0
            }
            previous = mono
        }
        remainder = combined.copyOfRange(complete, combined.size)
    }
    fun finish(): ShortArray {
        require(remainder.isEmpty()) { "Incomplete PCM frame. Record or export the audio again." }
        require(sampleCount > 0) { "Recording is empty" }
        return samples.copyOf(sampleCount)
    }
}

/** Host decoder only: no microphone, network, inference or action dispatch. */
class MemoPcmDecoder(private val context: Context) {
    suspend fun decode(uri: Uri, captureId: String): DecodedMemo = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { source ->
                require(source.length < 0 || source.length <= 12_000_000) { "Recording exceeds 12 MB. Split the recording." }
                if (source.length < 0) extractor.setDataSource(source.fileDescriptor)
                else extractor.setDataSource(source.fileDescriptor, source.startOffset, source.length)
            } ?: error("Recording unavailable. Relink it in Workspace.")
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track. Choose an audio recording.")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (format.containsKey(MediaFormat.KEY_DURATION)) require(format.getLong(MediaFormat.KEY_DURATION) <= 120_000_000) {
                "Recording exceeds two minutes. Split the recording."
            }
            val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            var adapter: MemoPcmAdapter? = null
            var inputDone = false
            var outputDone = false
            var compressed = 0L
            var lastProgress = android.os.SystemClock.elapsedRealtime()
            val info = MediaCodec.BufferInfo()
            while (!outputDone) {
                ensureActive()
                check(android.os.SystemClock.elapsedRealtime() - lastProgress < 10_000) { "Audio decoder stalled. Export the recording as M4A or WAV and retry." }
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val input = requireNotNull(decoder.getInputBuffer(index))
                        val count = extractor.readSampleData(input, 0)
                        if (count < 0) { decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else {
                            compressed += count
                            require(compressed <= 12_000_000 && extractor.sampleTime <= 120_000_000) { "Recording exceeds transcription limits. Split the recording." }
                            decoder.queueInputBuffer(index, 0, count, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                        lastProgress = android.os.SystemClock.elapsedRealtime()
                    }
                }
                when (val index = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val output = decoder.outputFormat
                        require(!output.containsKey(MediaFormat.KEY_PCM_ENCODING) || output.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_16BIT) {
                            "Unsupported PCM format. Export as 16-bit WAV or AAC/M4A."
                        }
                        check(adapter == null) { "Audio format changed mid-recording" }
                        adapter = MemoPcmAdapter(output.getInteger(MediaFormat.KEY_SAMPLE_RATE), output.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    }
                    else -> if (index >= 0) {
                        try {
                            if (info.size > 0) {
                                require(info.size <= 1_048_576) { "Audio buffer exceeds decode budget" }
                                val output = requireNotNull(decoder.getOutputBuffer(index))
                                output.position(info.offset); output.limit(info.offset + info.size)
                                val chunk = ByteArray(info.size); output.get(chunk)
                                (adapter ?: MemoPcmAdapter(rate, channels).also { adapter = it }).accept(chunk)
                            }
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            lastProgress = android.os.SystemClock.elapsedRealtime()
                        } finally { decoder.releaseOutputBuffer(index, false) }
                    }
                }
            }
            val samples = requireNotNull(adapter) { "Recording is empty" }.finish()
            val frames = (samples.indices step 16_000 * 20).map { offset ->
                AudioFrame(samples.copyOfRange(offset, minOf(offset + 16_000 * 20, samples.size)), 16_000, 1, offset * 1000L / 16_000)
            }
            DecodedMemo(AudioBuffer(frames, captureId), rate, channels, samples.size * 1000L / 16_000)
        } finally {
            codec?.let { runCatching { it.stop() }; it.release() }
            extractor.release()
        }
    }
}
