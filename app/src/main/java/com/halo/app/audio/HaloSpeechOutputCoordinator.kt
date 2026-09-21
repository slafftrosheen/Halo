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
    private val ttsProvider: AndroidTTSProvider
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
                    is TtsCancelled -> stop("TtsCancelled")
                    is TtsStarted -> {
                        // TtsStarted is emitted by the TTS provider. We re-emit it with the transactionId.
                        // Wait, AndroidTTSProvider doesn't emit TtsStarted, it uses UtteranceProgressListener.
                        // I'll emit it here inside `speak()`.
                    }
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
                val routePrepared = routeController.prepareRoute(event.target)
                if (!routePrepared && event.fallback == SpeechOutputFallback.DISALLOW_PHONE) {
                    Log.i("HaloAudio", "HALO_AUDIO_ROUTE_FAILED transaction=${event.transactionId} fallback=DISALLOW_PHONE")
                    eventBus.publish(
                        TtsFailed(
                            transactionId = event.transactionId,
                            target = event.target,
                            reason = "OUTPUT_ROUTE_UNAVAILABLE"
                        )
                    )
                    return@launch
                }

                // 2. Publish TtsStarted
                eventBus.publish(
                    TtsStarted(
                        transactionId = event.transactionId,
                        target = event.target,
                        textLength = event.text.length
                    )
                )

                // 3. Synthesize
                val request = SpeechRequest(text = event.text)
                val result = ttsProvider.synthesize(request)

                // 4. Publish result
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
        activeTransactionId = null
        scope.launch {
            ttsProvider.close() // Stops the current TTS utterance.
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
