package com.halo.app.controls

import com.halo.sdk.runtime.*
import com.halo.sdk.host.*
import com.halo.runtime.cognition.turn.TtsSanitizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.UUID

/** Only the last committed visible answer with a known speech policy is replayable. */
class CompletedAnswerReplay(private val events: EventBus, scope: CoroutineScope,
    private val actualTarget: (TtsStarted) -> SpeechOutputTarget? = { it.target.takeUnless { target -> target == SpeechOutputTarget.HostDefault } }) {
    @Volatile private var visible: AssistantResponsePublished? = null
    @Volatile private var speech: TtsRequested? = null
    private val policies = linkedMapOf<String, TtsRequested>()
    init { scope.launch(start = CoroutineStart.UNDISPATCHED) { events.events().collect { event ->
        when (event) {
            is AssistantResponsePublished -> { visible = event; speech = policies[event.transactionId] }
            is TtsRequested -> {
                policies[event.transactionId] = event.copy(text = "")
                if (policies.size > 8) policies.remove(policies.keys.first())
                if (event.transactionId == visible?.transactionId) speech = policies[event.transactionId]
            }
            is TtsStarted -> actualTarget(event)?.let { target ->
                policies[event.transactionId]?.let { policy ->
                    policies[event.transactionId] = policy.copy(target = target)
                    if (event.transactionId == visible?.transactionId) speech = policies[event.transactionId]
                }
            }
        }
    } } }
    suspend fun repeat(): AccessoryActionResult {
        val answer = visible ?: return AccessoryActionResult(AccessoryActionOutcome.UNAVAILABLE, "No completed answer")
        val policy = speech?.takeIf { it.transactionId == answer.transactionId && it.target != SpeechOutputTarget.HostDefault }
            ?: return AccessoryActionResult(AccessoryActionOutcome.UNAVAILABLE, "No verified reply route for this answer")
        val text = TtsSanitizer.sanitize(answer.text)
        if (text.isBlank()) return AccessoryActionResult(AccessoryActionOutcome.UNAVAILABLE, "Answer has no speakable text")
        return coroutineScope {
            val transaction = UUID.randomUUID().toString()
            val completion = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(120_000) { events.events().first {
                (it is TtsCompleted && it.transactionId == transaction) || (it is TtsFailed && it.transactionId == transaction) ||
                    (it is TtsCancelled && it.transactionId == transaction)
            } } }
            events.publish(policy.copy(id = UUID.randomUUID().toString(), transactionId = transaction,
                timestamp = System.currentTimeMillis(), text = text, sourceCaptureId = null))
            when (completion.await()) {
                is TtsCompleted -> AccessoryActionResult(AccessoryActionOutcome.EXECUTED, "Answer repeated")
                is TtsCancelled -> AccessoryActionResult(AccessoryActionOutcome.IGNORED, "Repeat stopped")
                else -> AccessoryActionResult(AccessoryActionOutcome.FAILED, "Repeat playback unavailable")
            }
        }
    }
}
