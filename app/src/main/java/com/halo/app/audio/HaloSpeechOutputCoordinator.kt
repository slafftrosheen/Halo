package com.halo.app.audio

import com.halo.sdk.runtime.*
import com.halo.providers.android.tts.AndroidTTSProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.halo.sdk.provider.SpeechRequest
import com.halo.engines.speech.ListeningStarted
import android.util.Log
import kotlinx.coroutines.CancellationException

class HaloSpeechOutputCoordinator(
    private val eventBus: EventBus,
    private val routeController: SpeechOutputRouteController,
    private val ttsProvider: AndroidTTSProvider,
    private val routedSpeechPlaybackProvider: RoutedSpeechPlaybackProvider? = null,
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventJob: Job? = null
    private var activeTransactionId: String? = null
    private var activeJob: Job? = null

    fun start() {
        if (eventJob?.isActive == true) return

        eventJob = scope.launch {
            eventBus.events().collect { event ->
                when (event) {
                    is TtsRequested -> speak(event)
                    is ListeningStarted -> stop("ListeningStarted")
                }
            }
        }
    }

    private fun speak(event: TtsRequested) {
        if (event.text.isBlank()) return

        activeJob?.cancel()
        activeTransactionId = event.transactionId

        activeJob = scope.launch {
            var playbackTarget = event.target
            try {
                // 1. Prepare the requested route.
                var routePreparation = routeController.prepareRoute(
                    SpeechRouteRequest(
                        transactionId = event.transactionId,
                        target = playbackTarget,
                        fallback = event.fallback,
                        sourceCaptureId = event.sourceCaptureId
                    )
                )

                if (routePreparation !is SpeechRoutePreparation.Ready) {
                    val reason = when (routePreparation) {
                        is SpeechRoutePreparation.Unavailable -> routePreparation.reason.name
                        is SpeechRoutePreparation.Pending -> "OUTPUT_ROUTE_PENDING"
                        is SpeechRoutePreparation.Ready -> error("Unreachable route state")
                    }

                    if (event.fallback == SpeechOutputFallback.DISALLOW_PHONE) {
                        Log.i(
                            "HaloAudio",
                            "HALO_AUDIO_ROUTE_FAILED transaction=${event.transactionId} " +
                                "reason=$reason fallback=${event.fallback}"
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
                    routePreparation = routeController.prepareRoute(
                        SpeechRouteRequest(
                            transactionId = event.transactionId,
                            target = playbackTarget,
                            fallback = SpeechOutputFallback.ALLOW_PHONE,
                            sourceCaptureId = event.sourceCaptureId
                        )
                    )
                    if (routePreparation !is SpeechRoutePreparation.Ready) {
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
                // 2. Synthesize with onStarted callback to publish TtsStarted when audible playback actually begins
                val request = SpeechRequest(text = event.text)
                val onStarted: () -> Unit = {
                    scope.launch {
                        nativeResourceCoordinator?.hint(NativeNextUseHint.EXPECT_ASR)
                        eventBus.publish(
                            TtsStarted(
                                transactionId = event.transactionId,
                                target = playbackTarget,
                                textLength = event.text.length
                            )
                        )
                    }
                    Unit
                }
                val result = if (playbackTarget is SpeechOutputTarget.Wearable) {
                    val evidence = (routePreparation as? SpeechRoutePreparation.Ready)?.evidence
                    if (evidence == null || routedSpeechPlaybackProvider == null) {
                        com.halo.sdk.provider.SpeechResult(0, false)
                    } else {
                        routedSpeechPlaybackProvider.synthesize(request, evidence, onStarted)
                    }
                } else {
                    ttsProvider.synthesize(request, onStarted)
                }

                // 3. Publish result
                if (result.successful) {
                    eventBus.publish(
                        TtsCompleted(
                            transactionId = event.transactionId,
                            target = playbackTarget
                        )
                    )
                } else {
                    eventBus.publish(
                        TtsFailed(
                            transactionId = event.transactionId,
                            target = playbackTarget,
                            reason = "TTS_FAILED"
                        )
                    )
                }
            } catch (e: CancellationException) {
                eventBus.publish(
                    TtsCancelled(
                        transactionId = event.transactionId,
                        target = playbackTarget
                    )
                )
                throw e
            } catch (e: Exception) {
                Log.e("HaloAudio", "TTS execution failed", e)
                eventBus.publish(
                    TtsFailed(
                        transactionId = event.transactionId,
                        target = playbackTarget,
                        reason = e.message ?: "UNKNOWN_ERROR"
                    )
                )
            } finally {
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
