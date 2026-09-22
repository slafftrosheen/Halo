package com.halo.app.audio

import com.halo.sdk.runtime.*
import com.halo.providers.android.tts.AndroidTTSProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.halo.sdk.provider.SpeechRequest
import com.halo.engines.speech.ListeningStarted
import android.util.Log
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineStart
import java.util.concurrent.atomic.AtomicBoolean

class HaloSpeechOutputCoordinator(
    context: Context,
    speechAudioAttributes: AudioAttributes,
    private val storage: Storage,
    private val eventBus: EventBus,
    private val routeController: SpeechOutputRouteController,
    private val ttsProvider: AndroidTTSProvider,
    private val routedSpeechPlaybackProvider: RoutedSpeechPlaybackProvider? = null,
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null
) {
    val session = HaloAudioSessionCoordinator()
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(speechAudioAttributes)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                stop("Audio focus lost")
            }
        }
        .build()
    @Volatile private var focusOwner: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventJob: Job? = null
    @Volatile private var activeTransactionId: String? = null
    @Volatile private var activeJob: Job? = null

    fun start() {
        if (eventJob?.isActive == true) return

        eventJob = scope.launch {
            eventBus.events().collect { event ->
                when (event) {
                    is TtsRequested -> speak(event)
                    is ListeningStarted -> {
                        stop("ListeningStarted")
                        session.listening(event.source, event.captureId)
                    }
                    is SpeechRecognized -> event.input?.let { session.thinking(it.route, it.captureId) }
                }
            }
        }
    }

    private fun speak(event: TtsRequested) {
        if (event.text.isBlank()) return
        val fallback = if (event.target is SpeechOutputTarget.Wearable) {
            if (storage.getBoolean("wearable_phone_fallback", false)) SpeechOutputFallback.ALLOW_PHONE
            else SpeechOutputFallback.DISALLOW_PHONE
        } else event.fallback

        activeJob?.cancel()
        activeTransactionId = event.transactionId
        session.preparing(event.target, event.transactionId)

        activeJob = scope.launch {
            var playbackTarget = event.target
            var startedEventJob: Job? = null
            val startedOnce = AtomicBoolean(false)
            try {
                // 1. Prepare the requested route.
                var routePreparation = routeController.prepareRoute(
                    SpeechRouteRequest(
                        transactionId = event.transactionId,
                        target = playbackTarget,
                        fallback = fallback,
                        sourceCaptureId = event.sourceCaptureId
                    )
                )

                if (routePreparation !is SpeechRoutePreparation.Ready) {
                    val reason = when (routePreparation) {
                        is SpeechRoutePreparation.Unavailable -> routePreparation.reason.name
                        is SpeechRoutePreparation.Pending -> "OUTPUT_ROUTE_PENDING"
                        is SpeechRoutePreparation.Ready -> error("Unreachable route state")
                    }

                    if (fallback == SpeechOutputFallback.DISALLOW_PHONE) {
                        session.failed(event.transactionId, HaloAudioFailure.ROUTE_UNAVAILABLE)
                        Log.i(
                            "HaloAudio",
                            "HALO_AUDIO_ROUTE_FAILED transaction=${event.transactionId} " +
                                "reason=$reason fallback=$fallback"
                        )
                        eventBus.publish(
                            TtsFailed(
                                transactionId = event.transactionId,
                                target = playbackTarget,
                                reason = reason
                            )
                        )
                        return@launch
                    }

                    Log.i(
                        "HaloAudio",
                        "HALO_AUDIO_ROUTE_FALLBACK transaction=${event.transactionId} " +
                            "from=${event.target::class.simpleName} to=Phone reason=$reason"
                    )
                    playbackTarget = SpeechOutputTarget.Phone
                    session.preparing(playbackTarget, event.transactionId)
                    routePreparation = routeController.prepareRoute(
                        SpeechRouteRequest(
                            transactionId = event.transactionId,
                            target = playbackTarget,
                            fallback = SpeechOutputFallback.ALLOW_PHONE,
                            sourceCaptureId = event.sourceCaptureId
                        )
                    )
                    if (routePreparation !is SpeechRoutePreparation.Ready) {
                        session.failed(event.transactionId, HaloAudioFailure.ROUTE_UNAVAILABLE)
                        eventBus.publish(
                            TtsFailed(
                                transactionId = event.transactionId,
                                target = playbackTarget,
                                reason = "PHONE_FALLBACK_ROUTE_FAILED"
                            )
                        )
                        return@launch
                    }
                }
                if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    session.failed(event.transactionId, HaloAudioFailure.FOCUS_DENIED)
                    eventBus.publish(TtsFailed(transactionId = event.transactionId, target = playbackTarget, reason = "AUDIO_FOCUS_DENIED"))
                    return@launch
                }
                focusOwner = event.transactionId
                // 2. Synthesize with onStarted callback to publish TtsStarted when audible playback actually begins
                val request = SpeechRequest(text = event.text)
                val onStarted: () -> Unit = {
                    if (activeTransactionId == event.transactionId &&
                        startedOnce.compareAndSet(false, true)) {
                        session.speaking(playbackTarget, event.transactionId,
                            (routePreparation as SpeechRoutePreparation.Ready).evidence)
                        startedEventJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            nativeResourceCoordinator?.hint(NativeNextUseHint.EXPECT_ASR)
                            eventBus.publish(
                                TtsStarted(
                                    transactionId = event.transactionId,
                                    target = playbackTarget,
                                    textLength = event.text.length
                                )
                            )
                        }
                    }
                    Unit
                }
                val result = if (playbackTarget is SpeechOutputTarget.Wearable) {
                    val evidence = (routePreparation as? SpeechRoutePreparation.Ready)?.evidence
                    if (evidence == null || routedSpeechPlaybackProvider == null) {
                        com.halo.sdk.provider.SpeechResult(0, false)
                    } else {
                        routedSpeechPlaybackProvider.synthesize(
                            request, evidence, onStarted,
                            playbackTarget as SpeechOutputTarget.Wearable
                        )
                    }
                } else {
                    ttsProvider.synthesize(request, onStarted)
                }

                // 3. Publish result
                startedEventJob?.join()
                if (result.successful) {
                    session.idle(event.transactionId)
                    eventBus.publish(
                        TtsCompleted(
                            transactionId = event.transactionId,
                            target = playbackTarget
                        )
                    )
                } else {
                    session.failed(event.transactionId, HaloAudioFailure.PLAYBACK_FAILED)
                    eventBus.publish(
                        TtsFailed(
                            transactionId = event.transactionId,
                            target = playbackTarget,
                            reason = "TTS_FAILED"
                        )
                    )
                }
            } catch (e: CancellationException) {
                session.idle(event.transactionId)
                withContext(NonCancellable) {
                    startedEventJob?.join()
                    eventBus.publish(
                        TtsCancelled(
                            transactionId = event.transactionId,
                            target = playbackTarget
                        )
                    )
                }
                throw e
            } catch (e: Exception) {
                session.failed(event.transactionId, HaloAudioFailure.PLAYBACK_FAILED)
                Log.e("HaloAudio", "TTS execution failed", e)
                eventBus.publish(
                    TtsFailed(
                        transactionId = event.transactionId,
                        target = playbackTarget,
                        reason = e.message ?: "UNKNOWN_ERROR"
                    )
                )
            } finally {
                if (focusOwner == event.transactionId) {
                    focusOwner = null
                    audioManager.abandonAudioFocusRequest(focusRequest)
                }
                if (activeTransactionId == event.transactionId) {
                    activeTransactionId = null
                }
            }
        }
    }

    fun stop(reason: String) {
        Log.i("HaloAudio", "Stopping speech output. Reason: $reason")
        activeJob?.cancel()
        activeJob = null
        activeTransactionId = null
        ttsProvider.stopCurrent()
        routedSpeechPlaybackProvider?.stopCurrent()
        // activeJob cancellation is the single cancellation event source. The
        // coroutine retains the actual playback target, including phone fallback.
    }

    fun shutdown() {
        eventJob?.cancel()
        eventJob = null
        activeJob?.cancel()
        activeJob = null
        activeTransactionId = null
        scope.launch {
            ttsProvider.close()
        }
    }
}
