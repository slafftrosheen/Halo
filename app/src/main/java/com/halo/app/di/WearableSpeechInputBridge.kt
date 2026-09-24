package com.halo.app.di

import android.content.Context
import android.util.Log
import com.halo.app.audio.ForegroundAudioSource
import com.halo.app.audio.HaloAudioForegroundService
import com.halo.drivers.heycyan.*
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.engines.speech.AudioPerceptionEngine
import com.halo.engines.speech.RecognitionFailed
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** All vendor boundaries, disconnects and watchdogs share one capture owner. */
class WearableSpeechInputBridge(
    context: Context,
    private val driver: RealHeyCyanDriver,
    private val engine: AudioPerceptionEngine,
    private val events: EventBus,
    private val scope: CoroutineScope,
    private val cancelSpeechOutput: () -> Unit
) {
    private val source = HeyCyanAudioSource(driver, externallyManaged = true)
    private val foregroundSource = ForegroundAudioSource(context, source, HaloAudioForegroundService.Kind.WEARABLE_INPUT)
    private val mutex = Mutex()
    private var captureId: String? = null
    private var worker: Job? = null
    private var watchdog: Job? = null

    fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            launch {
                driver.state.collect { state ->
                    if (state is com.halo.sdk.driver.DriverState.Disconnected || state is com.halo.sdk.driver.DriverState.Error) {
                        mutex.withLock { abort("Wearable disconnected during capture") }
                    }
                }
            }
            try {
                driver.heyCyanEvents.collect { event ->
                    mutex.withLock {
                        try {
                            when (event) {
                                HeyCyanEvent.VoiceActivationStarted -> {
                                    val deviceId = driver.activeDeviceId ?: return@withLock
                                    if (captureId != null) return@withLock
                                    // Physical voice activation is explicit barge-in; do not block the packet consumer on TTS cooldowns.
                                    cancelSpeechOutput()
                                    val turn = SpeechInputTurn(UUID.randomUUID().toString(),
                                        SpeechInputRoute.Wearable(deviceId, "HeyCyan"), ObservationSource.HeyCyanGlasses,
                                        CaptureBoundary.EXTERNAL)
                                    if (engine.acquireInput(turn, foregroundSource)) {
                                        captureId = turn.captureId
                                        watchdog = launch {
                                            delay(90_000L)
                                            mutex.withLock {
                                                if (captureId == turn.captureId) {
                                                    watchdog = null
                                                    abort("Wearable capture timed out waiting for its end signal")
                                                }
                                            }
                                        }
                                    }
                                }
                                is HeyCyanEvent.RawAudioFrame -> if (captureId != null) source.accept(event.payload)
                                HeyCyanEvent.VoiceActivationEnded -> {
                                    val id = captureId ?: return@withLock
                                    watchdog?.cancel(); watchdog = null
                                    withTimeout(5_000L) {
                                        source.finish()
                                        engine.finishExternalInput(id)
                                    }
                                    captureId = null
                                }
                                else -> Unit
                            }
                        } catch (error: TimeoutCancellationException) {
                            abort("Wearable audio did not finish in time")
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (error: Exception) {
                            Log.e("WEARABLE_AUDIO_END", "Capture failed", error)
                            abort(error.message ?: "Wearable capture failed")
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) { mutex.withLock { abort("Wearable audio stopped") } }
            }
        }
    }

    private suspend fun abort(reason: String) {
        val id = captureId ?: return
        captureId = null
        watchdog?.cancel(); watchdog = null
        try { engine.cancelExternalInput(id) }
        finally {
            foregroundSource.stop()
            events.publish(RecognitionFailed(id = id, reason = reason))
        }
    }
}
