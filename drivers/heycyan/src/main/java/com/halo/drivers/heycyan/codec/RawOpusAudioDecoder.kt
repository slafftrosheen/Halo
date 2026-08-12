package com.halo.drivers.heycyan.codec

import android.media.MediaCodec
import android.media.MediaFormat
import com.halo.sdk.host.AudioFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Raw Opus audio decoder for HeyCyan audio streams.
 *
 * Verifications from decompiled SDK (`OpusManager` with `hasHead = false`, packetSize = 40):
 * - Streams arrive as raw concatenated 40-byte Opus packets.
 * - Dynamic output format / sample rate handling via [MediaCodec.INFO_OUTPUT_FORMAT_CHANGED].
 */
class RawOpusAudioDecoder {
    private var decoder: MediaCodec? = null
    @Volatile private var isInitialized = false

    private val parser = HeyCyanOpusFrameParser()
    
    private val _frames = MutableSharedFlow<AudioFrame>(extraBufferCapacity = 64)
    val frames: Flow<AudioFrame> = _frames.asSharedFlow()

    private var baseTimestampUs = -1L
    private var currentSampleRate = 48000
    private var currentChannels = 1

    fun initialize() {
        if (isInitialized) return
        try {
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, 48000, 1)
            
            // Initializing CSD-0 header for Opus without container (hasHead = false)
            val rate = 48000
            val csd0 = byteArrayOf(
                'O'.code.toByte(), 'p'.code.toByte(), 'u'.code.toByte(), 's'.code.toByte(),
                'H'.code.toByte(), 'e'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), // OpusHead
                1, // Version
                1, // Channel count
                (312 and 0xFF).toByte(), ((312 shr 8) and 0xFF).toByte(), // Pre-skip
                (rate and 0xFF).toByte(), ((rate shr 8) and 0xFF).toByte(), ((rate shr 16) and 0xFF).toByte(), ((rate shr 24) and 0xFF).toByte(),
                0, 0, 0 // Gain & mapping family
            )
            // Fix OpusHead string bytes: 'O','p','u','s','H','e','a','d'
            csd0[4] = 'H'.code.toByte()
            csd0[5] = 'e'.code.toByte()
            csd0[6] = 'a'.code.toByte()
            csd0[7] = 'd'.code.toByte()

            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
            
            val csd1 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(6500000L).array()
            format.setByteBuffer("csd-1", ByteBuffer.wrap(csd1))

            val csd2 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(80000000L).array()
            format.setByteBuffer("csd-2", ByteBuffer.wrap(csd2))
            
            decoder?.configure(format, null, null, 0)
            decoder?.start()
            
            isInitialized = true
            
            Thread { drainOutput() }.start()
        } catch (e: Exception) {
            // Log or handle initialization failure
        }
    }

    fun decodePacket(payload: ByteArray, timestampUs: Long) {
        if (!isInitialized) initialize()

        // 40-byte Opus packet handling (hasHead = false)
        val analysis = parser.parse(payload)
        if (!analysis.isRfcValid) {
            return
        }
        
        val codec = decoder ?: return
        try {
            val inputBufferId = codec.dequeueInputBuffer(10000)
            if (inputBufferId >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferId)
                inputBuffer?.clear()
                inputBuffer?.put(payload)
                
                if (baseTimestampUs == -1L) baseTimestampUs = timestampUs
                val relativeTs = timestampUs - baseTimestampUs

                codec.queueInputBuffer(inputBufferId, 0, payload.size, relativeTs, 0)
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    private fun drainOutput() {
        val codec = decoder ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            while (isInitialized) {
                val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 100000)
                if (outputBufferId >= 0) {
                    val size = bufferInfo.size
                    if (size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferId)
                        if (outputBuffer != null) {
                            val chunk = ByteArray(size)
                            outputBuffer.get(chunk)
                            outputBuffer.clear()
                            
                            val shortArray = ShortArray(size / 2)
                            ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortArray)
                            
                            val frame = AudioFrame(
                                samples = shortArray,
                                sampleRate = currentSampleRate,
                                channels = currentChannels,
                                timestamp = System.currentTimeMillis()
                            )
                            _frames.tryEmit(frame)
                        }
                    }
                    codec.releaseOutputBuffer(outputBufferId, false)
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val format = codec.outputFormat
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        currentSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        currentChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun release() {
        isInitialized = false
        try {
            decoder?.stop()
            decoder?.release()
        } catch (e: Exception) {}
        decoder = null
    }
}
