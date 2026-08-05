package com.halo.host.android.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.halo.engines.speech.AudioFrame
import com.halo.engines.speech.AudioSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class AndroidAudioSource : AudioSource {
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    @SuppressLint("MissingPermission")
    override suspend fun start() {
        withContext(Dispatchers.IO) {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            audioRecord?.startRecording()
            isRecording = true
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }
    }

    override val frames: Flow<AudioFrame> = flow {
        val buffer = ShortArray(bufferSize / 2)
        while (currentCoroutineContext().isActive) {
            if (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val samples = buffer.copyOf(read)
                    emit(AudioFrame(samples, sampleRate, 1, System.currentTimeMillis()))
                }
            } else {
                delay(10)
            }
        }
    }
}
