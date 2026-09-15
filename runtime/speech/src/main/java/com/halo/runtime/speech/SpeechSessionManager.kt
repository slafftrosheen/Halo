package com.halo.runtime.speech

import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import com.halo.engines.speech.ListeningStarted
import com.halo.engines.speech.SpeechEnded
import com.halo.engines.speech.RecognitionStarted
import com.halo.engines.speech.RecognitionFinished
import com.halo.engines.speech.HandoffStarted
import com.halo.engines.speech.RecognitionFailed

class SpeechSessionManager(
    private val eventBus: EventBus,
    private val commandBus: CommandBus,
    private val scope: CoroutineScope
) : SpeechStateObserver {
    companion object {
        const val POST_TTS_LISTEN_COOLDOWN_MS = 250L
    }

    private val _sessionState = MutableStateFlow<SpeechSessionState>(SpeechSessionState.Idle)
    override val sessionState: StateFlow<SpeechSessionState> = _sessionState.asStateFlow()

    private var workerJob: Job? = null
    @Volatile private var outputActive = false
    @Volatile private var lastTtsEndTime = 0L

    fun isListeningAllowed(): Boolean {
        if (outputActive) return false
        if (System.currentTimeMillis() - lastTtsEndTime < POST_TTS_LISTEN_COOLDOWN_MS) return false
        if (_sessionState.value == SpeechSessionState.Speaking) return false
        return true
    }

    fun start() {
        if (workerJob?.isActive == true) return

        workerJob = scope.launch {
            launch { observeCommands() }
            launch { observeRecognitionEvents() }
            launch { observeTtsEvents() }
        }
    }

    fun stop() {
        workerJob?.cancel()
        workerJob = null
        outputActive = false
        _sessionState.value = SpeechSessionState.Idle
    }

    private suspend fun observeCommands() {
        coroutineScope {
            launch {
                commandBus.commands().filterIsInstance<StartListening>().collect {
                    if (isListeningAllowed()) {
                        _sessionState.value = SpeechSessionState.Capturing
                    }
                }
            }
            launch {
                commandBus.commands().filterIsInstance<StopListening>().collect {
                    if (_sessionState.value == SpeechSessionState.Capturing ||
                        _sessionState.value == SpeechSessionState.Recognizing
                    ) {
                        _sessionState.value = SpeechSessionState.Idle
                    }
                }
            }
        }
    }

    private suspend fun observeRecognitionEvents() {
        coroutineScope {
            launch {
                eventBus.observe(ListeningStarted::class.java).collect {
                    if (isListeningAllowed()) {
                        _sessionState.value = SpeechSessionState.Capturing
                    }
                }
            }
            launch {
                eventBus.observe(SpeechEnded::class.java).collect {
                    _sessionState.value = SpeechSessionState.Thinking
                }
            }
            launch {
                eventBus.observe(RecognitionStarted::class.java).collect {
                    if (isListeningAllowed()) {
                        _sessionState.value = SpeechSessionState.Recognizing
                    }
                }
            }
            launch {
                eventBus.observe(SpeechRecognized::class.java).collect {
                    _sessionState.value = SpeechSessionState.Thinking
                }
            }
            launch {
                eventBus.observe(HandoffStarted::class.java).collect {
                    _sessionState.value = SpeechSessionState.Handoff
                }
            }
            launch {
                eventBus.observe(RecognitionFinished::class.java).collect {
                    // Maintained until TTS or completion.
                }
            }
            launch {
                eventBus.observe(RecognitionFailed::class.java).collect {
                    _sessionState.value = SpeechSessionState.Recovering
                    delay(500)
                    _sessionState.value = SpeechSessionState.Idle
                }
            }
            launch {
                eventBus.observe(OutcomePublished::class.java).collect {
                    if (_sessionState.value == SpeechSessionState.Thinking) {
                        _sessionState.value = SpeechSessionState.Idle
                    }
                }
            }
        }
    }

    private suspend fun observeTtsEvents() {
        coroutineScope {
            launch {
                eventBus.observe(TtsStarted::class.java).collect {
                    outputActive = true
                    _sessionState.value = SpeechSessionState.Speaking
                }
            }
            launch {
                eventBus.observe(TtsCompleted::class.java).collect {
                    outputActive = false
                    lastTtsEndTime = System.currentTimeMillis()
                    _sessionState.value = SpeechSessionState.Idle
                }
            }
            launch {
                eventBus.observe(TtsFailed::class.java).collect {
                    outputActive = false
                    lastTtsEndTime = System.currentTimeMillis()
                    _sessionState.value = SpeechSessionState.Idle
                }
            }
        }
    }
}
