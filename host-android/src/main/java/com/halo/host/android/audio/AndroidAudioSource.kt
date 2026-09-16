package com.halo.host.android.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.halo.sdk.host.AudioFrame
import com.halo.sdk.host.AudioSource
import com.halo.sdk.host.AudioSourceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidAudioSource : AudioSource {
    private val _state = MutableStateFlow(AudioSourceState.IDLE)
    override val state: StateFlow<AudioSourceState> = _state.asStateFlow()

    @Volatile
    private var isRecording = false

    @Volatile
    private var audioRecord: AudioRecord? = null

    private val recorderMutex = Mutex()
    private var activeReader: AudioRecord? = null
    private var pendingRelease: AudioRecord? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private fun releaseRecorderSafely(recorder: AudioRecord?) {
        if (recorder == null) return
        runCatching {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
            recorder.release()
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun start() {
        withContext(Dispatchers.IO) {
            recorderMutex.withLock {
                if (isRecording && audioRecord != null) {
                    return@withLock
                }

                // Cleanly release any lingering previous AudioRecord instance
                isRecording = false
                releaseRecorderSafely(audioRecord)
                releaseRecorderSafely(pendingRelease)
                audioRecord = null
                pendingRelease = null
                activeReader = null

                check(bufferSize > 0) {
                    "Unable to determine a valid AudioRecord buffer size: $bufferSize"
                }

                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                try {
                    check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                        "AudioRecord failed to initialize"
                    }
                    recorder.startRecording()
                    check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        "AudioRecord failed to start recording"
                    }

                    audioRecord = recorder
                    isRecording = true
                    _state.value = AudioSourceState.ACTIVE
                } catch (exception: Exception) {
                    releaseRecorderSafely(recorder)
                    _state.value = AudioSourceState.ERROR
                    throw IllegalStateException("Unable to start AudioRecord", exception)
                }
            }
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            recorderMutex.withLock {
                isRecording = false
                _state.value = AudioSourceState.IDLE
                val recorderToRelease = audioRecord ?: pendingRelease
                audioRecord = null
                pendingRelease = null
                activeReader = null

                releaseRecorderSafely(recorderToRelease)
            }
        }
    }

    override val frames: Flow<AudioFrame> = flow {
        check(bufferSize > 0) {
            "Unable to determine a valid AudioRecord buffer size: $bufferSize"
        }
        val buffer = ShortArray(bufferSize / 2)
        var reader: AudioRecord? = null
        try {
            while (currentCoroutineContext().isActive) {
                val recorder = audioRecord
                if (!isRecording || recorder == null) {
                    break
                }

                val canRead = recorderMutex.withLock {
                    if (!isRecording || audioRecord !== recorder) {
                        false
                    } else {
                        activeReader = recorder
                        reader = recorder
                        true
                    }
                }
                if (!canRead) {
                    break
                }

                val read = try {
                    recorder.read(buffer, 0, buffer.size)
                } catch (exception: IllegalStateException) {
                    if (!isRecording) {
                        break
                    }
                    throw IllegalStateException("AudioRecord read failed", exception)
                }

                if (!isRecording) {
                    break
                }

                when {
                    read > 0 -> {
                        val samples = buffer.copyOf(read)
                        emit(AudioFrame(samples, sampleRate, 1, System.nanoTime()))
                    }
                    read == 0 -> delay(10)
                    read == AudioRecord.ERROR_INVALID_OPERATION -> {
                        throw IllegalStateException("AudioRecord read failed: invalid operation")
                    }
                    read == AudioRecord.ERROR_BAD_VALUE -> {
                        throw IllegalStateException("AudioRecord read failed: invalid buffer parameters")
                    }
                    read == AudioRecord.ERROR_DEAD_OBJECT -> {
                        throw IllegalStateException("AudioRecord read failed: recorder is no longer available")
                    }
                    else -> throw IllegalStateException("AudioRecord read failed with code $read")
                }
            }
        } finally {
            recorderMutex.withLock {
                if (activeReader === reader) {
                    activeReader = null
                }
            }
        }
    }
}
