package com.halo.runtime.speech

import com.halo.sdk.runtime.*
import com.halo.engines.speech.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One ordered event consumer owns speech state; a failed turn never latches input closed. */
class SpeechSessionManager(private val eventBus: EventBus, private val commandBus: CommandBus,
    private val scope: CoroutineScope) : SpeechStateObserver {
    companion object { const val POST_TTS_LISTEN_COOLDOWN_MS = 250L }
    private val mutableState = MutableStateFlow<SpeechSessionState>(SpeechSessionState.Idle)
    override val sessionState: StateFlow<SpeechSessionState> = mutableState.asStateFlow()
    private var workerJob: Job? = null
    private var recoveryJob: Job? = null
    private var captureId: String? = null
    private var cognitiveRequestId: String? = null
    private val closedCaptures = LinkedHashSet<String>()
    private fun closeCapture(id: String) {
        closedCaptures.add(id)
        while (closedCaptures.size > 256) closedCaptures.remove(closedCaptures.first())
    }
    @Volatile private var outputId: String? = null
    @Volatile private var lastTtsEndTime = 0L
    fun isListeningAllowed() = outputId == null &&
        System.currentTimeMillis() - lastTtsEndTime >= POST_TTS_LISTEN_COOLDOWN_MS

    fun start() {
        if (workerJob?.isActive == true) return
        workerJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                commandBus.commands().collect { command ->
                    when (command) {
                        is StopListening -> if (mutableState.value in setOf(SpeechSessionState.Capturing, SpeechSessionState.Recognizing, SpeechSessionState.Recovering)) {
                            recoveryJob?.cancel()
                            mutableState.value = SpeechSessionState.Idle
                        }
                        else -> Unit
                    }
                }
            }
            eventBus.events().collect { event ->
                when (event) {
                    is ListeningStarted -> {
                        if (event.captureId in closedCaptures) return@collect
                        recoveryJob?.cancel()
                        captureId = event.captureId
                        cognitiveRequestId = null
                        mutableState.value = SpeechSessionState.Capturing
                    }
                    is RecognitionStarted -> if (event.id == captureId && event.id !in closedCaptures) mutableState.value = SpeechSessionState.Recognizing
                    is SpeechEnded -> if (mutableState.value == SpeechSessionState.Capturing) mutableState.value = SpeechSessionState.Recognizing
                    is SpeechRecognized -> {
                        val recognizedCapture = event.input?.captureId ?: event.id
                        if (recognizedCapture in closedCaptures) return@collect
                        if (captureId != null && (event.input?.captureId ?: event.id) != captureId) return@collect
                        closeCapture(recognizedCapture)
                        captureId = event.input?.captureId ?: event.id
                        mutableState.value = SpeechSessionState.Thinking
                    }
                    is CognitiveRequestStarted -> cognitiveRequestId = event.requestId
                    is HandoffStarted -> mutableState.value = SpeechSessionState.Handoff
                    is RecognitionFailed -> {
                        if (event.id in closedCaptures) return@collect
                        if (captureId != null && event.id != captureId) return@collect
                        closeCapture(event.id)
                        mutableState.value = SpeechSessionState.Recovering
                        recoveryJob?.cancel()
                        recoveryJob = launch {
                            delay(500)
                            if (mutableState.value == SpeechSessionState.Recovering) mutableState.value = SpeechSessionState.Idle
                        }
                    }
                    is SpeechRejected -> if (event.captureId == null || event.captureId == captureId) {
                        if (event.reason == "CANCELLED") {
                            event.captureId?.let(::closeCapture)
                            recoveryJob?.cancel()
                            captureId = null
                            mutableState.value = SpeechSessionState.Idle
                        } else if (event.captureId != null) {
                            event.captureId?.let(::closeCapture)
                            recoveryJob?.cancel()
                            captureId = null
                            cognitiveRequestId = null
                            mutableState.value = if (outputId != null) SpeechSessionState.Speaking else SpeechSessionState.Idle
                        } else finishThinking()
                    }
                    is InteractionTurnChanged -> if (event.state is InteractionTurnState.Cancelled &&
                        event.state.interactionId == captureId) {
                        captureId?.let(::closeCapture)
                        recoveryJob?.cancel()
                        captureId = null
                        cognitiveRequestId = null
                        mutableState.value = SpeechSessionState.Idle
                    }
                    is CognitiveFailed -> if (matchesActiveCognitiveTurn(event.requestId)) {
                        cognitiveRequestId = null
                        finishThinking()
                    }
                    is CognitiveCompleted -> if (matchesActiveCognitiveTurn(event.requestId)) {
                        cognitiveRequestId = null
                        finishThinking()
                    }
                    is OutcomePublished -> if (cognitiveRequestId == null) finishThinking()
                    is TtsStarted -> {
                        outputId = event.transactionId
                        mutableState.value = SpeechSessionState.Speaking
                    }
                    is TtsCompleted -> finishOutput(event.transactionId, System.currentTimeMillis())
                    is TtsFailed -> finishOutput(event.transactionId, System.currentTimeMillis())
                    is TtsCancelled -> finishOutput(event.transactionId, 0L)
                    else -> Unit
                }
            }
        }
    }

    /**
     * Failure can be emitted immediately after speech recognition when provider
     * routing fails. Accept that terminal event while Thinking even if this
     * collector has not observed the corresponding start event yet.
     */
    private fun matchesActiveCognitiveTurn(requestId: String): Boolean =
        requestId == cognitiveRequestId ||
            (cognitiveRequestId == null && mutableState.value == SpeechSessionState.Thinking)

    private fun finishThinking() {
        if (mutableState.value in setOf(SpeechSessionState.Thinking, SpeechSessionState.Processing,
                SpeechSessionState.Handoff, SpeechSessionState.Recovering)) {
            recoveryJob?.cancel()
            mutableState.value = SpeechSessionState.Idle
        }
    }

    private fun finishOutput(id: String, endedAt: Long) {
        if (outputId != id) return
        outputId = null
        lastTtsEndTime = endedAt
        if (mutableState.value !in setOf(SpeechSessionState.Capturing, SpeechSessionState.Recognizing)) mutableState.value = SpeechSessionState.Idle
    }
    fun stop() {
        workerJob?.cancel(); workerJob = null
        recoveryJob?.cancel(); recoveryJob = null
        outputId = null
        captureId = null
        cognitiveRequestId = null
        mutableState.value = SpeechSessionState.Idle
    }
}
