package com.halo.host.android.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
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

/**
 * Single-owner phone microphone.
 *
 * The AudioRecord instance is created once and reused across user speech
 * sessions. Start/stop only changes the recording state; release happens at
 * host/runtime disposal. This mirrors the simple lifecycle used by the
 * Sherpa-ONNX Android microphone examples and avoids rapid stop/release/new
 * AudioRecord races on Android audio HALs.
 */
class AndroidAudioSource(
    private val context: Context? = null,
    private val expectedDeviceIdProvider: () -> Int? = { null }
) : AudioSource {
    private val audioManager = context?.applicationContext?.getSystemService(AudioManager::class.java)
    private val _state = MutableStateFlow(AudioSourceState.IDLE)
    override val state: StateFlow<AudioSourceState> = _state.asStateFlow()

    @Volatile
    private var isRecording = false

    @Volatile
    private var audioRecord: AudioRecord? = null

    private val recorderMutex = Mutex()

    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minimumBufferSizeBytes = AudioRecord.getMinBufferSize(
        sampleRate,
        channelConfig,
        audioFormat
    )

    // AudioRecord's internal buffer is measured in bytes, while read() below is
    // measured in shorts. Keep those units separate so the runtime receives a
    // stable 20 ms cadence regardless of the HAL's minimum buffer requirement.
    private val readChunkSamples = sampleRate / 50
    private val recorderBufferSizeBytes = maxOf(
        minimumBufferSizeBytes,
        readChunkSamples * BYTES_PER_PCM16_SAMPLE * 2
    )

    @SuppressLint("MissingPermission")
    private fun createRecorder(): AudioRecord {
        check(minimumBufferSizeBytes > 0) {
            "Unable to determine a valid AudioRecord buffer size: $recorderBufferSizeBytes"
        }

        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            recorderBufferSizeBytes
        ).also { recorder ->
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                error("AudioRecord failed to initialize")
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun start() {
        withContext(Dispatchers.IO) {
            recorderMutex.withLock {
                if (isRecording) return@withLock

                val recorder = audioRecord ?: createRecorder().also { audioRecord = it }

                try {
                    audioManager?.let { manager ->
                        val expectedId = expectedDeviceIdProvider()
                        val preferred = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                            .firstOrNull { if (expectedId != null) it.id == expectedId else it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
                            ?: error("Requested microphone is unavailable")
                        check(recorder.setPreferredDevice(preferred)) { "Unable to select the requested microphone" }
                    }
                    recorder.startRecording()
                    check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        "AudioRecord failed to start recording"
                    }
                    
                    // The actual route verification (e.g. confirming it's not silently falling back
                    // to the built-in mic when a Bluetooth headset was requested) must be performed
                    // by the caller or routing coordinator checking the routedDevice if needed.
                    
                    isRecording = true
                    _state.value = AudioSourceState.ACTIVE
                    Log.i("HaloAudio", "MIC_START routedDevice=${recorder.routedDevice?.type}")
                } catch (exception: Exception) {
                    // A failed platform recorder is unusable. Retire it before a later start.
                    isRecording = false
                    audioRecord = null
                    runCatching { recorder.release() }
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
                val recorder = audioRecord
                if (recorder != null && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    runCatching { recorder.stop() }
                        .onSuccess { Log.i("HaloAudio", "MIC_STOP") }
                }
                _state.value = AudioSourceState.IDLE
            }
        }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            recorderMutex.withLock {
                isRecording = false
                val recorder = audioRecord
                audioRecord = null
                if (recorder != null) {
                    runCatching {
                        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            recorder.stop()
                            Log.i("HaloAudio", "MIC_STOP")
                        }
                    }
                    runCatching { recorder.release() }
                }
                _state.value = AudioSourceState.IDLE
            }
        }
    }

    override val frames: Flow<AudioFrame> = flow {
        check(minimumBufferSizeBytes > 0) {
            "Unable to determine a valid AudioRecord buffer size: $recorderBufferSizeBytes"
        }

        val buffer = ShortArray(readChunkSamples)

        try {
            while (currentCoroutineContext().isActive) {
                val recorder = audioRecord
                if (!isRecording || recorder == null) break

                val read = try {
                    recorder.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                } catch (exception: IllegalStateException) {
                    if (!isRecording) break
                    throw IllegalStateException("AudioRecord read failed", exception)
                }

                if (!isRecording) break

                when {
                    read > 0 -> {
                        val expectedId = expectedDeviceIdProvider()
                        val actualDevice = recorder.routedDevice
                        if (audioManager != null) {
                            if (expectedId != null && actualDevice?.id != expectedId) {
                                throw IllegalStateException("Requested communication microphone route changed")
                            } else if (expectedId == null && actualDevice?.type != AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                                throw IllegalStateException("Phone microphone route changed; start listening again")
                            }
                        }
                        emit(
                            AudioFrame(
                                samples = buffer.copyOf(read),
                                sampleRate = sampleRate,
                                channels = 1,
                                timestamp = System.nanoTime()
                            )
                        )
                    }
                    read == 0 -> delay(5)
                    read == AudioRecord.ERROR_INVALID_OPERATION ->
                        throw IllegalStateException("AudioRecord read failed: invalid operation")
                    read == AudioRecord.ERROR_BAD_VALUE ->
                        throw IllegalStateException("AudioRecord read failed: invalid buffer parameters")
                    read == AudioRecord.ERROR_DEAD_OBJECT -> {
                        recorderMutex.withLock {
                            if (audioRecord === recorder) {
                                isRecording = false
                                audioRecord = null
                                runCatching { recorder.release() }
                            }
                        }
                        throw IllegalStateException("Phone microphone disconnected; start listening again")
                    }
                    else ->
                        throw IllegalStateException("AudioRecord read failed with code $read")
                }
            }
        } finally {
            // stop()/close() own recorder lifecycle. The frame collector only
            // relinquishes its read loop; it never releases the platform object.
        }
    }

    private companion object {
        const val BYTES_PER_PCM16_SAMPLE = 2
    }
}
