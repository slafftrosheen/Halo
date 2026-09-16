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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Android TTS host implementation.
 *
 * HeyCyan speaker selection has two layers:
 * 1. The BLE host sends the verified HeyCyan speaker-switch command.
 * 2. Android routes the actual TTS stream through its Bluetooth audio path.
 *
 * HeyCyan may expose its Classic Bluetooth speaker as A2DP rather than as a
 * communication device. Therefore TTS uses USAGE_MEDIA and explicitly asks
 * TextToSpeech for STREAM_MUSIC semantics. This keeps the utterance on the
 * Android media route instead of treating it as a phone-call stream.
 */
class AndroidTTSProvider(
    private val context: Context
) : SpeechSynthesisProvider, TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * TextToSpeech owns one process-wide utterance listener per engine.
     * Serialize synthesize() so concurrent calls cannot replace that listener,
     * restore a previous route while another utterance is playing, or leave
     * QUEUE_ADD utterances associated with the wrong route state.
     */
    private val synthesizeMutex = Mutex()

    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    private val initLock = Any()
    private var initCallback: ((Boolean) -> Unit)? = null

    private suspend fun ensureTts(): Boolean {
        if (isInitialized && tts != null) return true
        return suspendCancellableCoroutine { continuation ->
            synchronized(initLock) {
                if (isInitialized && tts != null) {
                    continuation.resume(true)
                    return@suspendCancellableCoroutine
                }
                val prev = initCallback
                initCallback = { ok ->
                    prev?.invoke(ok)
                    if (continuation.isActive) continuation.resume(ok)
                }
                if (tts == null) {
                    Log.i(TAG, "Initializing TextToSpeech lazily on demand")
                    tts = TextToSpeech(appContext, this)
                }
            }
        }
    }

    override fun onInit(status: Int) {
        synchronized(initLock) {
            val ok = status == TextToSpeech.SUCCESS
            if (ok) {
                tts?.language = Locale.US
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                isInitialized = true
            }
            val cb = initCallback
            initCallback = null
            cb?.invoke(ok)
        }
    }

    override suspend fun synthesize(request: SpeechRequest): SpeechResult =
        synthesizeMutex.withLock {
            val ready = ensureTts()
            if (!ready) return@withLock SpeechResult(0, false)
            suspendCancellableCoroutine { continuation ->
                val ttsEngine = tts
                if (ttsEngine == null || !isInitialized) {
                    continuation.resume(SpeechResult(0, false))
                    return@suspendCancellableCoroutine
                }

                val routeState = prepareBluetoothCommunicationRoute()
                val utteranceId = java.util.UUID.randomUUID().toString()
                val startTime = System.currentTimeMillis()
                var routeRestored = false

                fun restoreRouteOnce() {
                    if (routeRestored) return
                    routeRestored = true
                    restoreBluetoothCommunicationRoute(routeState)
                }

                fun finish(success: Boolean) {
                    if (!continuation.isActive) return
                    restoreRouteOnce()
                    val duration = if (success) System.currentTimeMillis() - startTime else 0L
                    continuation.resume(SpeechResult(duration, success))
                }

                ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        if (id == utteranceId) {
                            logCurrentBluetoothOutputs("TTS started")
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

                logCurrentBluetoothOutputs("TTS route before speak")
                val params = android.os.Bundle().apply {
                    // TTS engines may otherwise choose their own legacy stream.
                    // STREAM_MUSIC is the Android media path used by Classic A2DP.
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                }
                val result = ttsEngine.speak(
                    request.text,
                    TextToSpeech.QUEUE_ADD,
                    params,
                    utteranceId
                )
                if (result == TextToSpeech.ERROR && continuation.isActive) {
                    restoreRouteOnce()
                    continuation.resume(SpeechResult(0, false))
                }

                continuation.invokeOnCancellation {
                    ttsEngine.stop()
                    restoreRouteOnce()
                }
            }
        }

    /**
     * Prefer Android's explicit communication-device route for SCO/BLE headset
     * output. A2DP is intentionally left to Android's media routing policy.
     */
    private fun prepareBluetoothCommunicationRoute(): RouteState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            logCurrentBluetoothOutputs("TTS route API<31")
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
                logCurrentBluetoothOutputs("No Bluetooth communication device; using media route")
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
                "TTS Bluetooth communication route selected type=${communicationDevice.type} " +
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

    private fun logCurrentBluetoothOutputs(reason: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                .joinToString { "type=${it.type},name=${it.productName}" }

            val communication = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.communicationDevice?.let {
                    "type=${it.type},name=${it.productName},address=${it.address}"
                } ?: "none"
            } else {
                "legacy"
            }

            Log.i(
                TAG,
                "$reason | Bluetooth outputs=${outputs.ifBlank { "none" }} " +
                    "| communicationDevice=$communication | mode=${audioManager.mode} " +
                    "| musicActive=${audioManager.isMusicActive}"
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to inspect Bluetooth audio outputs", error)
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

    override suspend fun health(): Boolean = true

    override suspend fun close() {
        // Do not acquire synthesizeMutex here: synthesize() may be waiting for
        // an Android TTS callback while holding it. stop() is intentionally
        // allowed to trigger that callback and release the suspended call.
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private companion object {
        private const val TAG = "AndroidTTSProvider"
    }
}
