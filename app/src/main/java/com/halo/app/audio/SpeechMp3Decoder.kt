package com.halo.app.audio

import android.media.*
import android.os.SystemClock
import com.halo.sdk.provider.*
import kotlinx.coroutines.*

/** Bounded host-only MP3 decoding. No files, network access, or playback side effects. */
class SpeechMp3Decoder {
    suspend fun decode(bytes: ByteArray): PcmSpeech = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty() && bytes.size <= 12 * 1024 * 1024)
        val source = object : MediaDataSource() {
            override fun getSize() = bytes.size.toLong()
            override fun close() = Unit
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                if (position < 0 || position >= bytes.size) return -1
                val count = minOf(size, bytes.size - position.toInt())
                bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
                return count
            }
        }
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/mpeg"
            } ?: throw SpeechProviderException(SpeechErrorCode.INVALID_AUDIO_RESPONSE)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType("audio/mpeg").also { codec = it }
            decoder.configure(format, null, null, 0); decoder.start()
            var inputDone = false; var outputDone = false
            var sampleRate = 0; var channels = 0; var encoding: PcmEncoding? = null
            var size = 0L
            val chunks = mutableListOf<ByteArray>()
            val info = MediaCodec.BufferInfo()
            val deadline = SystemClock.elapsedRealtime() + 30000
            while (!outputDone) {
                ensureActive()
                check(SystemClock.elapsedRealtime() < deadline) { "Speech audio decoder timed out" }
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val input = requireNotNull(decoder.getInputBuffer(index))
                        val count = extractor.readSampleData(input, 0)
                        if (count < 0) { decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { decoder.queueInputBuffer(index, 0, count, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                when (val index = decoder.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(chunks.isEmpty()) { "Speech audio changed format" }
                        val output = decoder.outputFormat
                        sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        require(sampleRate in 8000..192000 && channels in 1..2)
                        encoding = when (if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) output.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT) {
                            AudioFormat.ENCODING_PCM_16BIT -> PcmEncoding.SIGNED_16_LE
                            AudioFormat.ENCODING_PCM_FLOAT -> PcmEncoding.FLOAT_32_LE
                            else -> throw SpeechProviderException(SpeechErrorCode.INVALID_AUDIO_RESPONSE)
                        }
                    }
                    else -> if (index >= 0) {
                        try {
                            if (info.size > 0) {
                                size += info.size
                                require(size <= 32 * 1024 * 1024 && encoding != null)
                                val buffer = requireNotNull(decoder.getOutputBuffer(index))
                                buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                chunks += ByteArray(info.size).also(buffer::get)
                            }
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        } finally { decoder.releaseOutputBuffer(index, false) }
                    }
                }
            }
            PcmSpeech(sampleRate, channels, requireNotNull(encoding), chunks)
        } finally {
            codec?.let { runCatching { it.stop() }; it.release() }
            extractor.release(); source.close()
        }
    }
}
