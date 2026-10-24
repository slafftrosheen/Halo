package com.halo.app.study

import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.TtsCancelled
import com.halo.sdk.runtime.TtsCompleted
import com.halo.sdk.runtime.TtsFailed
import com.halo.sdk.runtime.TtsStarted
import com.halo.sdk.runtime.TtsRequested
import com.halo.sdk.runtime.TtsRangeStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Read-aloud for Study through Halo's existing speech routing.
 *
 * Study does not own a TTS or MediaPlayer engine: it publishes the same [TtsRequested] event the
 * assistant uses, so output target, resource leases, route loss and cancellation stay inside the
 * process-wide coordinator. Each request carries the session and generation it belongs to, and:
 *
 * - a request for an older generation is dropped instead of replacing newer narration, and
 * - completion is reported back only for the utterance the reader actually asked for, so a stale
 *   callback can never advance the reader past a passage the learner has not heard.
 */
class SpeechOutputStudyNarration(
    private val eventBus: EventBus,
    private val cancelSpeech: (String) -> Unit,
    private val scope: CoroutineScope,
) : StudyNarrationController {

    override val available: Boolean = true

    private val mutableEvents = MutableSharedFlow<NarrationEvent>(extraBufferCapacity = 16)
    override val events: Flow<NarrationEvent> = mutableEvents.asSharedFlow()

    private data class ActiveNarration(val sessionId: String, val generationId: String, val transactionId: String)
    private val active = java.util.concurrent.atomic.AtomicReference<ActiveNarration?>(null)
    private var publishing: Job? = null

    private val eventJob: Job = scope.launch {
        eventBus.events().collect { event ->
            val transactionId = when (event) {
                is TtsStarted -> event.transactionId
                is TtsRangeStarted -> event.transactionId
                is TtsCompleted -> event.transactionId
                is TtsCancelled -> event.transactionId
                is TtsFailed -> event.transactionId
                else -> return@collect
            }
            // Only narration this controller started is reported, and only while it is still current.
            val current = active.get()?.takeIf { it.transactionId == transactionId } ?: return@collect
            val generationId = current.generationId
            if (event is TtsStarted) {
                mutableEvents.tryEmit(NarrationEvent.Started(generationId))
                return@collect
            }
            if(event is TtsRangeStarted) {
                mutableEvents.tryEmit(NarrationEvent.Range(generationId,event.start,event.end))
                return@collect
            }
            val narrationEvent = when (event) {
                is TtsCompleted -> NarrationEvent.Completed(generationId)
                is TtsCancelled -> NarrationEvent.Cancelled(generationId)
                is TtsFailed -> NarrationEvent.Failed(generationId, event.reason)
                else -> return@collect
            }
            if (!active.compareAndSet(current, null)) return@collect
            mutableEvents.tryEmit(narrationEvent)
        }
    }

    override fun narrate(text: String, sessionId: String, generationId: String) {
        narrate(text,sessionId,generationId,1.0,null)
    }
    @Synchronized override fun narrate(text: String, sessionId: String, generationId: String, speed: Double, language: String?) {
        if (text.isBlank()) {
            mutableEvents.tryEmit(NarrationEvent.Failed(generationId, "There is nothing to read"))
            return
        }
        val order = generationOrder(generationId)
        val previous = active.get()
        // Playback counters only order the same passage. A different lesson or
        // source is a new scope and may legitimately start with a lower counter.
        if (previous != null && previous.generationId.substringBefore(":reader:") == generationId.substringBefore(":reader:") &&
            order < generationOrder(previous.generationId)) return
        val transactionId = "study:$sessionId:$generationId"
        if (previous?.transactionId == transactionId) return
        active.set(ActiveNarration(sessionId, generationId, transactionId))
        previous?.let { cancelSpeech(it.transactionId) }
        publishAsync(TtsRequested(
            text = text,
            transactionId = transactionId,
            target = SpeechOutputTarget.HostDefault,
            fallback = SpeechOutputFallback.DISALLOW_PHONE,
            interactionId = generationId,
            speechRate = speed, language = language,
            localOnly = true,
        ))
    }

    @Synchronized override fun stop(reason: String) {
        publishing?.cancel()
        publishing = null
        active.getAndSet(null)?.let { cancelSpeech(it.transactionId) }
    }

    @Synchronized override fun stopSession(sessionId: String, reason: String) {
        if (active.get()?.sessionId == sessionId) stop(reason)
    }

    /** Cancels the collector; the coordinator stays alive because the process owns it. */
    fun shutdown() {
        stop("Study narration shutdown")
        eventJob.cancel()
    }

    /**
     * Generation ids end in the sentence index, so the trailing integer is the only part that orders
     * two readings of the same passage.
     */
    private fun generationOrder(generationId: String): Long =
        generationId.substringAfterLast(':').toLongOrNull() ?: 0L

    /**
     * The event bus is not a suspend-friendly API for hot paths, so publishing happens on the
     * controller's scope and never blocks the caller (the reader runs on the main thread).
     */
    private fun publishAsync(event: com.halo.sdk.runtime.HaloEvent) {
        publishing?.cancel()
        publishing = scope.launch {
            if (event is TtsRequested && active.get()?.transactionId != event.transactionId) return@launch
            eventBus.publish(event)
        }
    }
}
