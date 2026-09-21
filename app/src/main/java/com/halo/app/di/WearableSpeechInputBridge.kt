package com.halo.app.di

import com.halo.drivers.heycyan.*
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.engines.speech.AudioPerceptionEngine
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import java.util.UUID

/** Process-level adapter; physical input works independently of the visible screen. */
class WearableSpeechInputBridge(
    private val driver: RealHeyCyanDriver,
    private val engine: AudioPerceptionEngine,
    private val events: EventBus,
    private val scope: CoroutineScope
) {
    private val source = HeyCyanAudioSource(driver, externallyManaged = true)
    private var captureId: String? = null
    @Volatile private var speaking = false
    @Volatile private var quietAfter = 0L
    fun start() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { events.observe(TtsStarted::class.java).collect { speaking = true } }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { events.observe(TtsCompleted::class.java).collect { outputEnded() } }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { events.observe(TtsFailed::class.java).collect { outputEnded() } }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { events.observe(TtsCancelled::class.java).collect { outputEnded() } }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            driver.heyCyanEvents.collect { event ->
                try {
                    when (event) {
                        HeyCyanEvent.VoiceActivationStarted -> {
                            if (captureId == null && !speaking && System.currentTimeMillis() >= quietAfter) {
                                val deviceId = driver.activeDeviceId ?: return@collect
                                val turn = SpeechInputTurn(UUID.randomUUID().toString(),
                                    SpeechInputRoute.Wearable(deviceId, "HeyCyan"), ObservationSource.HeyCyanGlasses,
                                    CaptureBoundary.EXTERNAL)
                                if (engine.acquireInput(turn, source)) captureId = turn.captureId
                            } else android.util.Log.i("SPEECH_INPUT_ROUTE", "rejected=playback_or_existing_session")
                        }
                        is HeyCyanEvent.RawAudioFrame -> if (captureId != null) source.accept(event.payload)
                        HeyCyanEvent.VoiceActivationEnded -> {
                            val id = captureId ?: return@collect
                            captureId = null
                            source.finish()
                            engine.finishExternalInput(id)
                        }
                        else -> Unit
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) {
                    android.util.Log.e("WEARABLE_AUDIO_END", "reason=capture_failed", error)
                    val id = captureId; captureId = null
                    source.stop()
                    if (id != null) engine.finishExternalInput(id)
                }
            }
        }
        scope.launch {
            driver.state.collect { state ->
                if (state is com.halo.sdk.driver.DriverState.Disconnected || state is com.halo.sdk.driver.DriverState.Error) {
                    val id = captureId; captureId = null
                    if (id != null) { source.finish(); engine.finishExternalInput(id) }
                }
            }
        }
    }
    private fun outputEnded() { speaking = false; quietAfter = System.currentTimeMillis() + 750 }
}
