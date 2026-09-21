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
            try {
                // 1. Prepare Route
                val routePreparation = routeController.prepareRoute(
                    SpeechRouteRequest(
                        transactionId = event.transactionId,
                        target = event.target,
                        fallback = event.fallback,
                        sourceCaptureId = event.sourceCaptureId
                    )
                )
                when (routePreparation) {
                    is SpeechRoutePreparation.Unavailable -> {
                        Log.i("HaloAudio", "HALO_AUDIO_ROUTE_FAILED transaction=${event.transactionId} reason=${routePreparation.reason} fallback=${event.fallback}")
                        eventBus.publish(
                            TtsFailed(
                                transactionId = event.transactionId,
                                target = event.target,
                                reason = routePreparation.reason.name
                            )
                        )
                        if (event.fallback == SpeechOutputFallback.DISALLOW_PHONE) {
                            return@launch
                        }
                    }
                    is SpeechRoutePreparation.Pending -> {
                        Log.i("HaloAudio", "HALO_AUDIO_ROUTE_PENDING transaction=${event.transactionId} reason=${routePreparation.reason}")
                        eventBus.publish(
                            TtsFailed(
                                transactionId = event.transactionId,
                                target = event.target,
                                reason = "OUTPUT_ROUTE_PENDING"
                            )
                        )
                        if (event.fallback == SpeechOutputFallback.DISALLOW_PHONE) {
                            return@launch
                        }
                    }
                    is SpeechRoutePreparation.Ready -> {
                        // Route is ready
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
                                target = event.target,
                                textLength = event.text.length
                            )
                        )
                    }
                    Unit
                }
                val result = if (event.target is SpeechOutputTarget.Wearable) {
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
                            target = event.target
                        )
                    )
                } else {
                    eventBus.publish(
                        TtsFailed(
                            transactionId = event.transactionId,
                            target = event.target,
                            reason = "TTS_FAILED"
                        )
                    )
                }
            } catch (e: CancellationException) {
                eventBus.publish(
                    TtsCancelled(
                        transactionId = event.transactionId,
                        target = event.target
                    )
                )
                throw e
            } catch (e: Exception) {
                Log.e("HaloAudio", "TTS execution failed", e)
                eventBus.publish(
                    TtsFailed(
                        transactionId = event.transactionId,
                        target = event.target,
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
        val currentTx = activeTransactionId
        activeTransactionId = null
        ttsProvider.stopCurrent()
        routedSpeechPlaybackProvider?.stopCurrent()
        if (currentTx != null) {
            scope.launch {
                eventBus.publish(
                    TtsCancelled(
                        transactionId = currentTx
                    )
                )
            }
        }
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
