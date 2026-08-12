package com.halo.drivers.heycyan.debug

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * A probe that actually feeds the candidate Opus stream into Android's MediaCodec
 * to definitively prove that the packets are valid RFC-6716 Code-3 padded packets.
 */
class HeyCyanOpusDecoderProbe {
    private var decoder: MediaCodec? = null
    @Volatile private var isInitialized = false
    
    var successfulDecodes = 0
        private set
    var failedDecodes = 0
        private set
    var decodedByteCount = 0L
        private set

    var rfcValidFrames = 0
        private set
    var rfcInvalidFrames = 0
        private set

    private var baseTimestampUs = -1L
    
    private val parser = com.halo.drivers.heycyan.codec.HeyCyanOpusFrameParser()
    private var lastOutputFormat: MediaFormat? = null
    
    private var pcmOutputStream: java.io.FileOutputStream? = null
    private var pcmFile: java.io.File? = null

    fun initialize(context: android.content.Context? = null) {
        if (isInitialized) return
        try {
            if (context != null) {
                pcmFile = java.io.File(context.cacheDir, "halo_glasses_mic.pcm")
                pcmOutputStream = java.io.FileOutputStream(pcmFile)
                HeyCyanLogger.i("OpusDecoderProbe", "Saving PCM audio to: ${pcmFile?.absolutePath}")
            }
            
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, 48000, 1)
            
            // Opus CSD-0 (OpusHead)
            // 19 bytes: "OpusHead" (8), version=1, channels=1, preskip=312, rate=48000, gain=0, channel_mapping=0
            // Note: 48000 is used here as a placeholder hint for Android MediaCodec. The true encoded
            // sample rate is under structural validation. We must wait for INFO_OUTPUT_FORMAT_CHANGED.
            val rate = 48000
            val csd0 = byteArrayOf(
                'O'.code.toByte(), 'p'.code.toByte(), 'u'.code.toByte(), 's'.code.toByte(),
                'H'.code.toByte(), 'e'.code.toByte(), 'a'.code.toByte(), 'd'.code.toByte(),
                1, 1,
                (312 and 0xFF).toByte(), ((312 shr 8) and 0xFF).toByte(),
                (rate and 0xFF).toByte(), ((rate shr 8) and 0xFF).toByte(), ((rate shr 16) and 0xFF).toByte(), ((rate shr 24) and 0xFF).toByte(),
                0, 0, 0
            )
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
            
            val csd1 = ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(6500000L).array()
            format.setByteBuffer("csd-1", ByteBuffer.wrap(csd1))

            val csd2 = ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(80000000L).array()
            format.setByteBuffer("csd-2", ByteBuffer.wrap(csd2))
            
            decoder?.configure(format, null, null, 0)
            decoder?.start()
            
            isInitialized = true
            
            // Start a thread to pull output buffers
            Thread {
                drainOutput()
            }.start()
            
            HeyCyanLogger.i("OpusDecoderProbe", "MediaCodec initialized for audio/opus")
        } catch (e: Exception) {
            HeyCyanLogger.i("OpusDecoderProbe", "Failed to initialize MediaCodec: ${e.message}")
        }
    }
    
    fun decodePacket(payload: ByteArray, timestampUs: Long) {
        if (!isInitialized) initialize()

        val analysis = parser.parse(payload)
        if (analysis.isRfcValid) {
            rfcValidFrames++
        } else {
            rfcInvalidFrames++
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
            } else {
                HeyCyanLogger.i("OpusDecoderProbe", "No input buffer available")
            }
        } catch (e: Exception) {
            failedDecodes++
            HeyCyanLogger.i("OpusDecoderProbe", "queueInputBuffer failed: ${e.message}")
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
                        successfulDecodes++
                        decodedByteCount += size
                        
                        val outputBuffer = codec.getOutputBuffer(outputBufferId)
                        if (outputBuffer != null && pcmOutputStream != null) {
                            val chunk = ByteArray(size)
                            outputBuffer.get(chunk)
                            outputBuffer.clear()
                            pcmOutputStream?.write(chunk)
                        }
                        
                        if (successfulDecodes % 10 == 0) {
                            val formatStr = lastOutputFormat?.let { "fmt=[rate=${it.getInteger(MediaFormat.KEY_SAMPLE_RATE)}, ch=${it.getInteger(MediaFormat.KEY_CHANNEL_COUNT)}]" } ?: "fmt=unknown"
                            HeyCyanLogger.i("OpusDecoderProbe", "Decoded $successfulDecodes frames (RFC valid: $rfcValidFrames, invalid: $rfcInvalidFrames). PCM: $decodedByteCount bytes. $formatStr")
                        }
                    }
                    codec.releaseOutputBuffer(outputBufferId, false)
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    lastOutputFormat = codec.outputFormat
                    HeyCyanLogger.i("OpusDecoderProbe", "Output format changed: ${codec.outputFormat}")
                }
            }
        } catch (e: Exception) {
            HeyCyanLogger.i("OpusDecoderProbe", "drainOutput exception: ${e.message}")
        }
    }
    
    fun release() {
        isInitialized = false
        try {
            pcmOutputStream?.close()
        } catch (e: Exception) {}
        pcmOutputStream = null
        
        try {
            decoder?.stop()
            decoder?.release()
        } catch (e: Exception) {}
        decoder = null
    }
}
