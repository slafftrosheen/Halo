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
import com.halo.runtime.cognition.device.DefaultInteractionRoutePolicy
import com.halo.runtime.cognition.device.InteractionRoutePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineStart
import java.util.concurrent.atomic.AtomicBoolean

import com.halo.sdk.runtime.resource.ResourceLeaseManager
import com.halo.sdk.runtime.resource.ResourceYieldDecision
import com.halo.sdk.runtime.resource.ResourceYieldHandler

class HaloSpeechOutputCoordinator(
    context: Context,
    speechAudioAttributes: AudioAttributes,
    private val storage: Storage,
    private val eventBus: EventBus,
    private val routeController: SpeechOutputRouteController,
    private val ttsProvider: AndroidTTSProvider,
    private val routedSpeechPlaybackProvider: RoutedSpeechPlaybackProvider? = null,
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null,
    private val interactionRoutePolicy: InteractionRoutePolicy = DefaultInteractionRoutePolicy(),
    resourceLeaseManager: ResourceLeaseManager? = null,
) {
    val session = HaloAudioSessionCoordinator(
        requireNotNull(resourceLeaseManager) {
            "HaloSpeechOutputCoordinator requires the process-wide ResourceLeaseManager"
        }
    )
    private val applicationContext = context.applicationContext
    private val audioManager = applicationContext.getSystemService(AudioManager::class.java)
    private val speechAttributes = speechAudioAttributes
    private val playbackMutex = kotlinx.coroutines.sync.Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventJob: Job? = null
    @Volatile private var activeTransactionId: String? = null
    @Volatile private var activeJob: Job? = null

    fun start() {
        if (eventJob?.isActive == true) return

        eventJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val cognitionCaptures = mutableMapOf<String, String>()
            eventBus.events().collect { event ->
                when (event) {
                    is TtsRequested -> speak(event)
                    is ListeningStarted -> {
                        stop("ListeningStarted")
                        session.listening(event.source, event.captureId)
                        // MINING-P0-001 route affinity begins here, when the interaction's
                        // input/origin is established — before STT, cognition or inference.
                        session.bindInteraction(
                            interactionId = event.captureId,
                            inputRoute = event.source,
                        )
                    }
                    is SpeechRecognized -> event.input?.let { session.thinking(it.route, it.captureId) }
                    is CognitiveRequestStarted -> (session.state.value as? HaloAudioSessionState.Thinking)?.let {
                        cognitionCaptures[event.requestId] = it.captureId
                    }
                    is CognitiveCompleted -> cognitionCaptures.remove(event.requestId)?.let(session::finishInput)
                    is CognitiveFailed -> cognitionCaptures.remove(event.requestId)?.let(session::finishInput)
                    is SpeechRejected -> event.captureId?.let(session::finishInput)
                    is com.halo.engines.speech.RecognitionFailed -> session.finishInput(event.id)
                }
            }
        }
    }

    /**
     * MINING-P0-001 the audio-route transition for a failed wearable route never plays on the
     * phone when policy is DISALLOW_PHONE: the handoff decision above returns Unavailable and
     * publishes TtsFailed, and the affinity records UNAVAILABLE.
     */
    private fun speak(event: TtsRequested) {
        if (event.text.isBlank()) {
            scope.launch { eventBus.publish(TtsFailed(transactionId = event.transactionId, target = event.target, reason = "Speech text is empty", interactionId = event.interactionId)) }
            return
        }
        val fallback = event.fallback

        activeJob?.cancel()
        activeTransactionId = event.transactionId
        session.preparing(event.target, event.transactionId)

        activeJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var playbackTarget = event.target
            var startedEventJob: Job? = null
            val startedOnce = AtomicBoolean(false)
            var locked = false
            var focusGranted = false
            var outputLease: OutputLease? = null
            var foregroundLease: HaloAudioForegroundService.Lease? = null
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(speechAttributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener { change ->
                    if (change in setOf(AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) && activeTransactionId == event.transactionId) {
                        stop("Audio focus lost")
                    }
                }.build()
            try {
                playbackMutex.lock()
                locked = true
                outputLease = session.acquireOutput(
                    interactionId = event.transactionId,
                    deviceId = (event.target as? SpeechOutputTarget.Device)?.deviceId,
                    ownerYieldHandler = ResourceYieldHandler {
                        // Stop the real playback owner. The coroutine's finally
                        // restores focus/route and only then releases the lease.
                        stop("Yielding audio output to higher-priority input")
                        ResourceYieldDecision.RELEASING
                    },
                ).getOrThrow()
                foregroundLease = HaloAudioForegroundService.acquire(applicationContext, HaloAudioForegroundService.Kind.PLAYBACK)
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

                    val continuity = com.halo.runtime.cognition.device.InteractionContinuity(
                        com.halo.cognition.language.InteractionId(event.transactionId),
                        (event.target as? SpeechOutputTarget.Device)?.deviceId, "AudioInput", "AudioOutput",
                        allowHandoff = fallback == SpeechOutputFallback.ALLOW_PHONE)
                    val handoff = interactionRoutePolicy.evaluateHandoff(continuity, listOf(
                        com.halo.runtime.cognition.device.RuntimeDevice("local_phone", "phone", "Phone",
                            capabilities = setOf(com.halo.runtime.cognition.device.DeviceCapability.AudioOutput()))))
                    if (handoff is com.halo.runtime.cognition.device.HandoffDecision.Unavailable) {
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
                                reason = reason,
                                interactionId = event.interactionId,
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
                    // Explicit policy handoff: recorded as a real transition, not a silent re-route.
                    session.transitionRoute(
                        interactionId = event.transactionId,
                        state = com.halo.sdk.runtime.InteractionAudioRouteState.PENDING,
                        intendedTarget = SpeechOutputTarget.Phone,
                        fallback = SpeechOutputFallback.ALLOW_PHONE,
                    )
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
                                reason = "PHONE_FALLBACK_ROUTE_FAILED",
                                interactionId = event.interactionId,
                            )
                        )
                        return@launch
                    }
                }
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
                                    textLength = event.text.length,
                                    interactionId = event.interactionId,
                                )
                            )
                        }
                    }
                    Unit
                }
                val evidence = (routePreparation as? SpeechRoutePreparation.Ready)?.evidence
                    ?: error("Speech route has no endpoint evidence")
                // Route resolved and endpoint verified: record it as the interaction's binding.
                // Never the raw address, only the platform display label.
                session.transitionRoute(
                    interactionId = event.transactionId,
                    state = com.halo.sdk.runtime.InteractionAudioRouteState.BOUND,
                    endpointLabel = evidence.androidDeviceName,
                    intendedTarget = playbackTarget,
                    fallback = fallback,
                )
                val playback = routedSpeechPlaybackProvider ?: error("Routed speech playback is unavailable")
                val result = when (val routed = playback.synthesizeRouted(request, evidence, onStarted, playbackTarget, acquireFocus = {
                    focusGranted = activeTransactionId == event.transactionId &&
                        audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                    focusGranted
                })) {
                    is RoutedPlaybackResult.Completed -> com.halo.sdk.provider.SpeechResult(routed.durationMs, true)
                    RoutedPlaybackResult.Cancelled -> throw CancellationException("Speech playback cancelled")
                    RoutedPlaybackResult.RouteLost -> error("Speech audio route disconnected or changed")
                    is RoutedPlaybackResult.Failed -> error(routed.reason)
                }

                // 3. Publish result
                startedEventJob?.join()
                if (result.successful) {
                    session.idle(event.transactionId)
                    eventBus.publish(
                        TtsCompleted(
                            transactionId = event.transactionId,
                            target = playbackTarget,
                            interactionId = event.interactionId,
                        )
                    )
                } else {
                    session.failed(event.transactionId, HaloAudioFailure.PLAYBACK_FAILED)
                    eventBus.publish(
                        TtsFailed(
                            transactionId = event.transactionId,
                            target = playbackTarget,
                            reason = "TTS_FAILED",
                            interactionId = event.interactionId,
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
                            target = playbackTarget,
                            interactionId = event.interactionId,
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
                        reason = e.message ?: "UNKNOWN_ERROR",
                        interactionId = event.interactionId,
                    )
                )
            } finally {
                if (focusGranted) runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
                try {
                    foregroundLease?.release()
                } finally {
                    outputLease?.release()
                    if (locked) playbackMutex.unlock()
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
        stop("Shutdown")
        scope.launch {
            playbackMutex.lock()
            try { ttsProvider.close() } finally { playbackMutex.unlock() }
        }
    }
}
