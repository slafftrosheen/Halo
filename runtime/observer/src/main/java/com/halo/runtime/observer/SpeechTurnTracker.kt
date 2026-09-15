package com.halo.runtime.observer

import com.halo.sdk.runtime.CognitiveCompleted
import com.halo.sdk.runtime.CognitiveFailed
import com.halo.sdk.runtime.CognitiveRequestStarted
import com.halo.sdk.runtime.CognitiveReasoningStarted
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloEvent
import com.halo.sdk.runtime.SpeechRecognized
import com.halo.sdk.runtime.TtsCompleted
import com.halo.sdk.runtime.TtsFailed
import com.halo.sdk.runtime.TtsStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque

data class SpeechTurnMetrics(
    val turnIndex: Int,
    val recognizedText: String,
    val responseText: String?,
    val sttToCognitiveMs: Long,
    val cognitiveMs: Long,
    val cognitiveToTtsMs: Long,
    val ttsMs: Long,
    val totalMs: Long,
    val completedAt: Long = System.currentTimeMillis(),
    val failed: Boolean = false,
    val failureReason: String? = null
)

class SpeechTurnTracker(
    private val eventBus: EventBus,
    private val scope: CoroutineScope
) {
    companion object {
        const val MAX_TURNS = 50
    }

    private val _turns = MutableStateFlow<List<SpeechTurnMetrics>>(emptyList())
    val turns: StateFlow<List<SpeechTurnMetrics>> = _turns.asStateFlow()

    private val _currentTurn = MutableStateFlow<InProgressTurn?>(null)

    private var turnCounter = 0
    private val deque = ArrayDeque<SpeechTurnMetrics>(MAX_TURNS)

    init {
        scope.launch {
            eventBus.events().collect { event ->
                onEvent(event)
            }
        }
    }

    private fun onEvent(event: HaloEvent) {
        when (event) {
            is SpeechRecognized -> {
                val previous = _currentTurn.value
                if (previous != null) {
                    finishTurn(SpeechTurnMetrics(
                        turnIndex = previous.turnIndex,
                        recognizedText = previous.recognizedText,
                        responseText = previous.responseText,
                        sttToCognitiveMs = previous.cognitiveRequestStartedAt?.let { it - previous.speechRecognizedAt } ?: -1L,
                        cognitiveMs = previous.cognitiveCompletedAt?.let { it - (previous.cognitiveRequestStartedAt ?: previous.speechRecognizedAt) } ?: -1L,
                        cognitiveToTtsMs = -1L,
                        ttsMs = -1L,
                        totalMs = event.timestamp - previous.speechRecognizedAt,
                        failed = true,
                        failureReason = "Interrupted by new speech"
                    ))
                }
                _currentTurn.value = InProgressTurn(
                    turnIndex = ++turnCounter,
                    recognizedText = event.text,
                    speechRecognizedAt = event.timestamp
                )
            }

            is CognitiveRequestStarted -> {
                _currentTurn.value = _currentTurn.value?.copy(
                    cognitiveRequestStartedAt = event.timestamp
                )
            }

            is CognitiveReasoningStarted -> {
                _currentTurn.value = _currentTurn.value?.copy(
                    reasoningStartedAt = event.timestamp
                )
            }

            is CognitiveCompleted -> {
                _currentTurn.value = _currentTurn.value?.copy(
                    cognitiveCompletedAt = event.timestamp,
                    responseText = event.response
                )
            }

            is CognitiveFailed -> {
                val turn = _currentTurn.value ?: return
                finishTurn(SpeechTurnMetrics(
                    turnIndex = turn.turnIndex,
                    recognizedText = turn.recognizedText,
                    responseText = null,
                    sttToCognitiveMs = turn.cognitiveRequestStartedAt?.let { it - turn.speechRecognizedAt } ?: -1L,
                    cognitiveMs = event.timestamp - (turn.cognitiveRequestStartedAt ?: turn.speechRecognizedAt),
                    cognitiveToTtsMs = -1L,
                    ttsMs = -1L,
                    totalMs = event.timestamp - turn.speechRecognizedAt,
                    failed = true,
                    failureReason = event.message
                ))
                _currentTurn.value = null
            }

            is TtsStarted -> {
                _currentTurn.value = _currentTurn.value?.copy(
                    ttsStartedAt = event.timestamp
                )
            }

            is TtsCompleted -> {
                val turn = _currentTurn.value ?: return
                val ttsStartedAt = turn.ttsStartedAt ?: return
                finishTurn(SpeechTurnMetrics(
                    turnIndex = turn.turnIndex,
                    recognizedText = turn.recognizedText,
                    responseText = turn.responseText,
                    sttToCognitiveMs = turn.cognitiveRequestStartedAt?.let { it - turn.speechRecognizedAt } ?: -1L,
                    cognitiveMs = turn.cognitiveCompletedAt?.let { completed ->
                        turn.cognitiveRequestStartedAt?.let { completed - it }
                    } ?: -1L,
                    cognitiveToTtsMs = ttsStartedAt - (turn.cognitiveCompletedAt ?: ttsStartedAt),
                    ttsMs = event.timestamp - ttsStartedAt,
                    totalMs = event.timestamp - turn.speechRecognizedAt
                ))
                _currentTurn.value = null
            }

            is TtsFailed -> {
                val turn = _currentTurn.value ?: return
                finishTurn(SpeechTurnMetrics(
                    turnIndex = turn.turnIndex,
                    recognizedText = turn.recognizedText,
                    responseText = turn.responseText,
                    sttToCognitiveMs = turn.cognitiveRequestStartedAt?.let { it - turn.speechRecognizedAt } ?: -1L,
                    cognitiveMs = turn.cognitiveCompletedAt?.let { completed ->
                        turn.cognitiveRequestStartedAt?.let { completed - it }
                    } ?: -1L,
                    cognitiveToTtsMs = turn.ttsStartedAt?.let { it - (turn.cognitiveCompletedAt ?: it) } ?: -1L,
                    ttsMs = event.timestamp - (turn.ttsStartedAt ?: event.timestamp),
                    totalMs = event.timestamp - turn.speechRecognizedAt,
                    failed = true,
                    failureReason = event.message
                ))
                _currentTurn.value = null
            }
        }
    }

    private fun finishTurn(metrics: SpeechTurnMetrics) {
        if (deque.size >= MAX_TURNS) deque.removeFirst()
        deque.addLast(metrics)
        _turns.value = deque.toList()
    }

    private data class InProgressTurn(
        val turnIndex: Int,
        val recognizedText: String,
        val speechRecognizedAt: Long,
        val cognitiveRequestStartedAt: Long? = null,
        val reasoningStartedAt: Long? = null,
        val cognitiveCompletedAt: Long? = null,
        val ttsStartedAt: Long? = null,
        val responseText: String? = null
    )
}
