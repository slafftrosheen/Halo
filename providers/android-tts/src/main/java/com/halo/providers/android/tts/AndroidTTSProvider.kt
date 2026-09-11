package com.halo.providers.android.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.halo.sdk.host.AudioFrame
import com.halo.sdk.provider.SpeechRequest
import com.halo.sdk.provider.SpeechResult
import com.halo.sdk.provider.SpeechSynthesisProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Android TTS host implementation.
 *
 * HeyCyan speaker selection has two layers:
 * 1. The BLE host sends the verified HeyCyan speaker-switch command.
 * 2. Android routes the actual PCM/TTS stream through its Bluetooth audio path.
 *
 * API 31+ exposes the supported communication-device API. We only select a
 * Bluetooth SCO/BLE headset when Android reports one as an available
 * communication device. We deliberately do not force SCO when only A2DP is
 * available: A2DP is a media route and should remain under Android's normal
 * media/assistant routing policy.
 */
class AndroidTTSProvider(
    private val context: Context
) : SpeechSynthesisProvider, TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            isInitialized = true
        }
    }

    override suspend fun synthesize(request: SpeechRequest): SpeechResult =
        suspendCancellableCoroutine { continuation ->
            val ttsEngine = tts
            if (ttsEngine == null || !isInitialized) {
                continuation.resume(SpeechResult(0, false))
                return@suspendCancellableCoroutine
            }

            val routeState = prepareBluetoothCommunicationRoute()
            val utteranceId = java.util.UUID.randomUUID().toString()
            val startTime = System.currentTimeMillis()

            fun finish(success: Boolean) {
                if (!continuation.isActive) return
                restoreBluetoothCommunicationRoute(routeState)
                val duration = if (success) System.currentTimeMillis() - startTime else 0L
                continuation.resume(SpeechResult(duration, success))
            }

            ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    if (id == utteranceId) {
                        Log.d(TAG, "TTS started utterance=$utteranceId")
                    }
                }

                override fun onDone(id: String?) {
                    if (id == utteranceId) finish(true)
                }

                override fun onError(id: String?) {
                    if (id == utteranceId) finish(false)
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId) {
                        Log.w(TAG, "TTS failed utterance=$utteranceId errorCode=$errorCode")
                        finish(false)
                    }
                }
            })

            val result = ttsEngine.speak(
                request.text,
                TextToSpeech.QUEUE_ADD,
                null,
                utteranceId
            )
            if (result == TextToSpeech.ERROR && continuation.isActive) {
                restoreBluetoothCommunicationRoute(routeState)
                continuation.resume(SpeechResult(0, false))
            }

            continuation.invokeOnCancellation {
                ttsEngine.stop()
                restoreBluetoothCommunicationRoute(routeState)
            }
        }

    /**
     * Prefer Android's explicit communication-device route for SCO/BLE headset
     * output. A2DP is intentionally left to the system media route.
     */
    private fun prepareBluetoothCommunicationRoute(): RouteState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return RouteState.unmodified
        }

        return runCatching {
            val previousMode = audioManager.mode
            val previousDevice = audioManager.communicationDevice
            val communicationDevice = audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }

            if (communicationDevice == null) {
                logCurrentBluetoothOutputs()
                return RouteState.unmodified
            }

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            val selected = audioManager.setCommunicationDevice(communicationDevice)
            if (!selected) {
                audioManager.mode = previousMode
                Log.w(TAG, "Android rejected Bluetooth communication route")
                return RouteState.unmodified
            }

            Log.i(
                TAG,
                "TTS Bluetooth route selected type=${communicationDevice.type} " +
                    "name=${communicationDevice.productName}"
            )
            RouteState(previousMode, previousDevice, modified = true)
        }.getOrElse { error ->
            Log.w(TAG, "Unable to prepare Bluetooth TTS route", error)
            RouteState.unmodified
        }
    }

    private fun restoreBluetoothCommunicationRoute(state: RouteState) {
        if (!state.modified || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        runCatching {
            if (state.previousDevice != null) {
                audioManager.setCommunicationDevice(state.previousDevice)
            } else {
                audioManager.clearCommunicationDevice()
            }
            audioManager.mode = state.previousMode
        }.onFailure { error ->
            Log.w(TAG, "Unable to restore Android audio route", error)
        }
    }

    private fun logCurrentBluetoothOutputs() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                .joinToString { "type=${it.type},name=${it.productName}" }
            Log.i(TAG, "Available Bluetooth audio outputs: ${outputs.ifBlank { "none" }}")
        }
    }

    private data class RouteState(
        val previousMode: Int,
        val previousDevice: AudioDeviceInfo?,
        val modified: Boolean,
    ) {
        companion object {
            val unmodified = RouteState(
                previousMode = AudioManager.MODE_NORMAL,
                previousDevice = null,
                modified = false,
            )
        }
    }

    override fun stream(text: String): Flow<AudioFrame> = emptyFlow()

    override suspend fun health(): Boolean = isInitialized

    override suspend fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private companion object {
        private const val TAG = "AndroidTTSProvider"
    }
}
