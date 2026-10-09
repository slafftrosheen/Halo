package com.halo.app.audio

import com.halo.sdk.runtime.*
import com.halo.providers.android.tts.AndroidTTSProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asStateFlow
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineStart
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.update

import com.halo.sdk.runtime.resource.ResourceLeaseManager
import com.halo.sdk.runtime.resource.ResourceYieldDecision
import com.halo.sdk.runtime.resource.ResourceYieldHandler

data class SpeechOutputTestState(val transactionId: String? = null, val requested: String = "Android default",
    val observed: String? = null, val outcome: String = "Not tested", val audibleConfirmed: Boolean = false)

class HaloSpeechOutputCoordinator(
    context: Context,
    speechAudioAttributes: AudioAttributes,
    private val storage: Storage,
    private val eventBus: EventBus,
    private val routeController: SpeechRouteAuthority,
    private val ttsProvider: com.halo.sdk.provider.PcmSpeechSynthesisProvider,
    private val synthesisRegistry: com.halo.app.ai.SpeechSynthesisRegistry? = null,
    private val routedSpeechPlaybackProvider: SpeechChunkPlayback? = null,
    private val nativeResourceCoordinator: NativeResourceCoordinator? = null,
    private val interactionRoutePolicy: InteractionRoutePolicy = DefaultInteractionRoutePolicy(),
    resourceLeaseManager: ResourceLeaseManager? = null,
    private val voiceSettings: () -> VoiceExperienceSettings = { VoiceExperienceSettings() },
    private val cancelGeneration: () -> Unit = {},
    private val playbackScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val acquireForeground: suspend () -> (suspend () -> Unit) = {
        val lease = HaloAudioForegroundService.acquire(context.applicationContext, HaloAudioForegroundService.Kind.PLAYBACK)
        val release: suspend () -> Unit = { lease.release() }
        release
    },
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
    private val scope = playbackScope
    private var eventJob: Job? = null
    @Volatile private var activeTransactionId: String? = null
    @Volatile private var activeJob: Job? = null
    @Volatile private var activeInteractionId: String? = null
    private val finalSpeechReceipts = LinkedHashSet<String>()
    private val streamPreparationReceipts = LinkedHashSet<String>()
    private fun claimReceipt(receipts: MutableSet<String>, id: String): Boolean {
        if (!receipts.add(id)) return false
        while (receipts.size > 256) receipts.remove(receipts.first())
        return true
    }

    private val interruption = VoiceInterruptionCoordinator(voiceSettings,
        { stop("Barge-in") }, cancelGeneration, log = { Log.i("HALO_VOICE_INTERRUPTION", it) })

    private val mutablePlaybackActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val playbackActive = mutablePlaybackActive.asStateFlow()
    private val mutableOutputTest = kotlinx.coroutines.flow.MutableStateFlow(SpeechOutputTestState())
    val outputTest = mutableOutputTest.asStateFlow()
    private fun updateTest(id: String, transform: (SpeechOutputTestState) -> SpeechOutputTestState) {
        mutableOutputTest.update { if (it.transactionId == id) transform(it) else it }
    }
    private fun finishTest(id: String, outcome: String) = updateTest(id) {
        if (it.outcome in setOf("Preparing", "Route verified · Playing")) it.copy(outcome = outcome) else it
    }
    @Synchronized fun testOutput(target: SpeechOutputTarget, label: String) {
        stop("Audio test requested")
        val id = java.util.UUID.randomUUID().toString()
        mutableOutputTest.value = SpeechOutputTestState(id, label, outcome = "Preparing")
        speak(TtsRequested(text = "This is Halo. Your audio output test is playing.", transactionId = id,
            target = target, fallback = SpeechOutputFallback.DISALLOW_PHONE))
    }
    fun stopOutputTest() {
        if (activeTransactionId == mutableOutputTest.value.transactionId) stop("Audio test stopped")
    }
    fun confirmOutputTest() { mutableOutputTest.update { if (it.outcome == "Playback completed" && it.observed != null) it.copy(audibleConfirmed = true) else it } }
    private var streaming: StreamingVoiceResponse? = null
    private val stoppedResponses = LinkedHashSet<String>()
    private val mutableStreamingStatus = kotlinx.coroutines.flow.MutableStateFlow("No streamed reply this session")
    val streamingStatus = mutableStreamingStatus.asStateFlow()
    private fun rememberStopped(id: String) = synchronized(stoppedResponses) {
        stoppedResponses.add(id)
        while (stoppedResponses.size > 256) stoppedResponses.remove(stoppedResponses.first())
    }
    private fun isStopped(id: String) = synchronized(stoppedResponses) { id in stoppedResponses }
    private fun streamLog(kind: String, response: StreamingVoiceResponse, reason: String = "NONE") {
        Log.i("HALO_VOICE_STREAM", "event=$kind request=${response.prepared.requestId.take(8)} " +
            "chunks=${response.chunkCount} audibleChunks=${response.audibleChunks} firstTokenMs=${response.firstTokenAt?.minus(response.openedAt)?.div(1_000_000)} firstSpeakableMs=${response.firstChunkAt?.minus(response.openedAt)?.div(1_000_000)} synthesisReadyMs=${response.synthesisReadyAt?.minus(response.openedAt)?.div(1_000_000)} timeToAudibleMs=${response.audibleAt?.minus(response.openedAt)?.div(1_000_000)} targetKind=${if (response.prepared.target is SpeechOutputTarget.Phone) "PHONE" else "DEVICE"} " +
            "audibleStarted=${response.audible} reasonCode=$reason")
        mutableStreamingStatus.value = "${if (response.audible) "Spoken stream" else "Buffered reply"} · ${response.audibleChunks} spoken chunks · $kind"
    }
    @Synchronized private fun prepareStream(event: StreamingSpeechPrepared) {
        if (isStopped(event.requestId) || !claimReceipt(streamPreparationReceipts, event.requestId)) return
        stop("New response")
        streaming = StreamingVoiceResponse(event).also { streamLog("OPEN", it) }
    }
    @Synchronized private fun acceptToken(event: CognitiveTokenReceived) {
        val response = streaming?.takeIf { it.prepared.requestId == event.requestId &&
            it.prepared.interactionId == event.interactionId && !isStopped(event.requestId) } ?: return
        if (response.halted) return
        val previousChunks = response.chunkCount
        response.accept(event.token)
        if (activeTransactionId != event.requestId) {
            val first = response.chunks.tryReceive().getOrNull() ?: return
            speak(TtsRequested(text = first, transactionId = event.requestId, target = response.prepared.target,
                fallback = response.prepared.fallback, sourceCaptureId = response.prepared.sourceCaptureId,
                interactionId = response.prepared.interactionId), streamResponse = response)
        }
        if (response.chunkCount != previousChunks) streamLog("CHUNK", response)
    }
    @Synchronized private fun finalSpeech(event: TtsRequested) {
        if (isStopped(event.transactionId) || !claimReceipt(finalSpeechReceipts, event.transactionId)) return
        val response = streaming?.takeIf { it.prepared.requestId == event.transactionId }
        if (response == null) { stop("New spoken response"); speak(event); return }
        when (response.finish(event.text)) {
            StreamingVoiceResponse.Finish.FULL_RESPONSE -> {
                activeJob?.cancel()
                routedSpeechPlaybackProvider?.stopCurrent()
                synthesisRegistry?.stopCurrent() ?: ttsProvider.stopCurrent()
                streaming = null
                streamLog("FALLBACK", response, "NO_AUDIBLE_CHUNK")
                speak(event)
            }
            StreamingVoiceResponse.Finish.SUFFIX -> scope.launch {
                try {
                    if (response.remainder.isNotBlank()) response.chunks.send(response.remainder)
                    response.chunks.close()
                } catch (_: CancellationException) { }
            }
            StreamingVoiceResponse.Finish.MISMATCH -> {
                streamLog("CANCEL", response, "FINAL_MISMATCH")
                stop("FINAL_MISMATCH")
            }
            StreamingVoiceResponse.Finish.STOP -> Unit
        }
    }

    fun start() {
        if (eventJob?.isActive == true) return

        eventJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val cognitionCaptures = mutableMapOf<String, String>()
            eventBus.events().collect { event ->
                interruption.onEvent(event)
                when (event) {
                    is TtsCompleted -> finishTest(event.transactionId, "Playback completed")
                    is TtsCancelled -> finishTest(event.transactionId, "Stopped")
                    is TtsFailed -> finishTest(event.transactionId, "Failed: ${event.message}")
                    is TtsRequested -> finalSpeech(event)
                    is StreamingSpeechPrepared -> prepareStream(event)
                    is CognitiveTokenReceived -> acceptToken(event)
                    is InteractionTurnChanged -> if (event.state is InteractionTurnState.Cancelled || event.state is InteractionTurnState.Failed || event.state is InteractionTurnState.Interrupted) {
                        if (streaming?.prepared?.interactionId == event.state.interactionId || activeInteractionId == event.state.interactionId) stop("Cognition ended")
                    }
                    is ListeningStarted -> {
                        session.listening(event.source, event.captureId)
                        // Prewarm wearable output during STT + cognition latency window
                        val inputSource = event.source
                        if (inputSource is SpeechInputRoute.Device && inputSource.kind == AudioDeviceKind.WEARABLE) {
                            val deviceId = inputSource.deviceId
                            if (deviceId != null) {
                                scope.launch {
                                    Log.i("HaloAudio", "HALO_AUDIO_PREWARM_BEGIN device=$deviceId capture=${event.captureId}")
                                    routeController.prewarmRoute(deviceId)
                                    Log.i("HaloAudio", "HALO_AUDIO_PREWARM_DONE device=$deviceId capture=${event.captureId}")
                                }
                            }
                        }
                        // MINING-P0-001 route affinity begins here, when the interaction's
                        // input/origin is established — before STT, cognition or inference.
                        session.bindInteraction(
                            interactionId = event.captureId,
                            inputRoute = event.source,
                            fallback = fallbackFor(event.source, voiceSettings().wearablePhoneFallback),
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
    fun preview(providerId: String) {
        speak(TtsRequested(text = "Halo voice test.", transactionId = java.util.UUID.randomUUID().toString(),
            target = SpeechOutputTarget.Phone,
            fallback = SpeechOutputFallback.DISALLOW_PHONE), providerId)
    }

    private fun speak(event: TtsRequested, previewProviderId: String? = null, streamResponse: StreamingVoiceResponse? = null) {
        if (event.text.isBlank()) {
            scope.launch { eventBus.publish(TtsFailed(transactionId = event.transactionId, target = event.target, reason = "Speech text is empty", interactionId = event.interactionId)) }
            return
        }
        val fallback = event.fallback

        activeJob?.cancel()
        activeTransactionId = event.transactionId
        activeInteractionId = event.interactionId
        session.preparing(event.target, event.transactionId)

        val responseJob = scope.launch(start = CoroutineStart.LAZY) {
            var playbackTarget = event.target
            var startedEventJob: Job? = null
            val startedOnce = AtomicBoolean(false)
            var locked = false
            var focusGranted = false
            var outputLease: OutputLease? = null
            var releaseForeground: (suspend () -> Unit)? = null
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
                releaseForeground = acquireForeground()
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
                        if (streamResponse != null) error(reason)
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
                        if (streamResponse != null) error("PHONE_FALLBACK_ROUTE_FAILED")
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
                val sanitized = com.halo.runtime.cognition.turn.TtsSanitizer.sanitize(event.text)
                check(sanitized.isNotBlank()) { "No speakable response" }
                val speechRequest = SpeechRequest(text = sanitized, language=event.language,
                    voiceSettings=mapOf("speed" to event.speechRate.coerceIn(0.6,1.5)),
                    onPlaybackRange=if(sanitized == event.text) { start,end ->
                        if(activeTransactionId == event.transactionId) scope.launch {
                            eventBus.publish(TtsRangeStarted(event.transactionId,start,end))
                        }
                    } else null)
                val (pinnedProvider, request) = synthesisRegistry?.pin(speechRequest, previewProviderId)
                    ?: (ttsProvider to speechRequest)
                val measuredProvider = object : com.halo.sdk.provider.PcmSpeechSynthesisProvider,
                    com.halo.sdk.provider.SpeechSynthesisProvider by pinnedProvider {
                    override suspend fun synthesizeToPcm(request: SpeechRequest): com.halo.sdk.provider.PcmSpeech? =
                        pinnedProvider.synthesizeToPcm(request).also { if (it != null) streamResponse?.synthesized() }
                }
                val onStarted: () -> Unit = {
                    if (streamResponse != null && !streamResponse.started()) throw CancellationException("Stream discarded")
                    if (activeTransactionId == event.transactionId &&
                        startedOnce.compareAndSet(false, true)) {
                        mutablePlaybackActive.value = true
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
                var evidence = (routePreparation as? SpeechRoutePreparation.Ready)?.evidence
                    ?: error("Speech route has no endpoint evidence")
                val playback = routedSpeechPlaybackProvider ?: error("Routed speech playback is unavailable")
                var chunk: String? = sanitized
                while (chunk != null) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val chunkRequest = request.copy(text = chunk, onPlaybackRange = request.onPlaybackRange.takeIf { chunk == sanitized })
                    when (val routed = playback.synthesizeRouted(chunkRequest, evidence, onStarted, playbackTarget, acquireFocus = {
                        if (!focusGranted) focusGranted = activeTransactionId == event.transactionId &&
                            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                        focusGranted
                    }, provider = measuredProvider, onRouteVerified = { actual ->
                        updateTest(event.transactionId) { it.copy(observed = actual.androidDeviceName ?: "Verified Android endpoint", outcome = "Route verified · Playing") }
                        evidence = actual
                        routePreparation = SpeechRoutePreparation.Ready(playbackTarget, actual)
                        session.transitionRoute(interactionId = event.transactionId,
                            state = com.halo.sdk.runtime.InteractionAudioRouteState.BOUND,
                            endpointLabel = actual.androidDeviceName, intendedTarget = event.target, fallback = fallback)
                    })) {
                        is RoutedPlaybackResult.Completed -> { routed.evidence?.let { evidence = it } }
                        RoutedPlaybackResult.Cancelled -> throw CancellationException("Speech playback cancelled")
                        RoutedPlaybackResult.RouteLost -> error("ROUTE_LOST")
                        is RoutedPlaybackResult.Failed -> error(routed.reason)
                    }
                    chunk = streamResponse?.chunks?.receiveCatching()?.getOrNull()
                }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val result = com.halo.sdk.provider.SpeechResult(0, true)

                // 3. Publish result
                startedEventJob?.join()
                if (result.successful) {
                    streamResponse?.let { streamLog("COMPLETE", it); rememberStopped(event.transactionId) }
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
                if (streamResponse?.replacedByFullResponse == true) throw e
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
                if (streamResponse != null && !streamResponse.audible) {
                    streamLog("FALLBACK", streamResponse, "PRE_AUDIO_FAILURE")
                    streamResponse.failBeforeAudio()
                    // Preserve the final-authority fallback; no public terminal event yet.
                    return@launch
                }
                rememberStopped(event.transactionId)
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
                if (streamResponse == null || streamResponse.audible) rememberStopped(event.transactionId)
                if (streamResponse?.audible == true) streamResponse.chunks.cancel()
                withContext(NonCancellable) {
                    if (focusGranted) runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
                    try {
                        releaseForeground?.invoke()
                    } finally {
                        try { outputLease?.release() }
                        finally { if (locked) playbackMutex.unlock() }
                    }
                }
                if (activeJob == kotlinx.coroutines.currentCoroutineContext()[Job]) {
                    mutablePlaybackActive.value = false
                    activeTransactionId = null
                    activeInteractionId = null
                }
            }
        }
        activeJob = responseJob
        responseJob.start()
    }

    /** Stop audible output without replay. Unspoken buffered streams may use the final new route. */
    @Synchronized fun handoffInteraction(interactionId: String) {
        val response = streaming?.takeIf { it.prepared.interactionId == interactionId }
        if (response != null && !response.audible && !mutablePlaybackActive.value) {
            response.finish("")
            streaming = null
            activeJob?.cancel(); activeJob = null; activeTransactionId = null
            synthesisRegistry?.stopCurrent() ?: ttsProvider.stopCurrent()
            routedSpeechPlaybackProvider?.stopCurrent()
        } else if (session.affinity.value?.interactionId == interactionId) stop("Explicit endpoint handoff")
    }

    @Synchronized fun stop(reason: String) {
        Log.i("HaloAudio", "Stopping speech output. Reason: $reason")
        val unstarted = streaming?.takeIf { activeJob?.isActive != true && !isStopped(it.prepared.requestId) }
        unstarted?.let { response -> scope.launch { eventBus.publish(TtsCancelled(
            transactionId = response.prepared.requestId, target = response.prepared.target,
            interactionId = response.prepared.interactionId)) } }
        streaming?.let { rememberStopped(it.prepared.requestId); streamLog("CANCEL", it, "STOP"); it.abandon() }
        streaming = null
        activeTransactionId?.let(::rememberStopped)
        activeJob?.cancel()
        activeJob = null
        mutablePlaybackActive.value = false
        activeTransactionId = null
        activeInteractionId = null
        synthesisRegistry?.stopCurrent() ?: ttsProvider.stopCurrent()
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
            try { synthesisRegistry?.close() ?: ttsProvider.close() } finally { playbackMutex.unlock() }
        }
    }
}
