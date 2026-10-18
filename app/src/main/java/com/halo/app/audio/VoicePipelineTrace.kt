package com.halo.app.audio

import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ephemeral, bounded, content-free evidence for issue #33 voice latency.
 * The reducer is an observer, never an execution or consent authority.
 *
 * A buffered STT endpoint is recorded before native decode. Live recognition
 * has no reliable endpoint timestamp: its end-of-speech metric stays null.
 * TtsStarted is reported by the audio output owner after playback actually starts.
 */
data class VoicePipelineSnapshot(
    val interactionId: String,
    val captureId: String?,
    val captureStartedAtMs: Long?,
    val speechEndedAtMs: Long?,
    val transcriptReadyAtMs: Long?,
    val firstTokenAtMs: Long?,
    val playbackStartedAtMs: Long?,
    val stageDurationsMs: Map<String, Long>,
    val state: String,
) {
    val endOfSpeechToFirstAudioMs: Long?
        get() = duration(speechEndedAtMs, playbackStartedAtMs)
    val endOfSpeechToTranscriptMs: Long?
        get() = duration(speechEndedAtMs, transcriptReadyAtMs)
    val transcriptToFirstTokenMs: Long?
        get() = duration(transcriptReadyAtMs, firstTokenAtMs)
    val captureToFirstAudioMs: Long?
        get() = duration(captureStartedAtMs, playbackStartedAtMs)

    private fun duration(start: Long?, end: Long?): Long? =
        if (start == null || end == null || end < start) null else end - start
}

class VoicePipelineTrace(private val capacity: Int = 64) {
    init { require(capacity in 1..256) }
    private data class Row(
        var interactionId: String,
        var captureId: String? = null,
        var captureStartedAt: Long? = null,
        var speechEndedAt: Long? = null,
        var transcriptAt: Long? = null,
        var tokenAt: Long? = null,
        var playbackAt: Long? = null,
        var outputTransactionId: String? = null,
        var state: String = "CAPTURING",
        val stages: MutableMap<String, Long> = linkedMapOf(),
    ) {
        fun snapshot() = VoicePipelineSnapshot(interactionId, captureId, captureStartedAt,
            speechEndedAt, transcriptAt, tokenAt, playbackAt, stages.toMap(), state)
    }

    private val rows = LinkedHashMap<String, Row>()
    private val aliases = LinkedHashMap<String, String>()
    private val requests = LinkedHashMap<String, String>()
    private val mutable = MutableStateFlow<List<VoicePipelineSnapshot>>(emptyList())
    val traces = mutable.asStateFlow()

    private fun canonical(id: String): String = aliases[id] ?: id
    private fun row(id: String): Row {
        val key = canonical(id)
        return rows.getOrPut(key) { Row(key) }
    }

    /** Streaming response identity may arrive after inference has already begun.
     * Merge earlier stage evidence without resetting any recorded milestone.
     */
    private fun bind(interactionId: String, captureId: String?) {
        if (captureId.isNullOrBlank() || interactionId == captureId) return
        val existingKey = canonical(interactionId)
        val captureKey = canonical(captureId)
        if (existingKey == captureKey) return
        val from = rows.remove(existingKey)
        val destination = row(captureKey)
        destination.interactionId = interactionId
        destination.captureId = captureId
        if (from != null) {
            destination.captureStartedAt = destination.captureStartedAt ?: from.captureStartedAt
            destination.speechEndedAt = destination.speechEndedAt ?: from.speechEndedAt
            destination.transcriptAt = destination.transcriptAt ?: from.transcriptAt
            destination.tokenAt = destination.tokenAt ?: from.tokenAt
            destination.playbackAt = destination.playbackAt ?: from.playbackAt
            destination.outputTransactionId = destination.outputTransactionId ?: from.outputTransactionId
            from.stages.forEach { (stage, duration) ->
                destination.stages.putIfAbsent(stage, duration)
            }
        }
        aliases[interactionId] = captureKey
    }

    /**
     * Returns a snapshot only when the incoming event changed a tracked stage.
     * Do not store transcript, token, speech text, tool arguments or provider secrets.
     */
    @Synchronized fun onEvent(event: HaloEvent): VoicePipelineSnapshot? {
        var changed: Row? = null
        when (event) {
            is InteractionLatencyUpdated -> {
                val trace = event.trace
                val r = row(trace.interactionId)
                r.captureId = trace.interactionId
                r.captureStartedAt = trace.startedAtMs
                r.speechEndedAt = trace.speechEndedAtMs ?: r.speechEndedAt
                changed = r
            }
            is SpeechRecognized -> {
                val id = event.input?.captureId ?: event.id
                val r = row(id)
                r.captureId = id
                r.transcriptAt = event.timestamp
                r.state = "TRANSCRIBED"
                changed = r
            }
            is StreamingSpeechPrepared -> {
                bind(event.interactionId, event.sourceCaptureId)
                val r = row(event.interactionId)
                if (r.outputTransactionId != event.requestId) {
                    r.outputTransactionId = event.requestId
                    r.playbackAt = null
                }
                changed = r
            }
            is CognitiveRequestStarted -> {
                val id = event.interactionId
                if (!id.isNullOrBlank()) {
                    requests[event.requestId] = id
                    val r = row(id)
                    r.state = "PREPARING"
                    changed = r
                }
            }
            is CognitiveTokenReceived -> {
                val id = event.interactionId ?: requests[event.requestId]
                if (!id.isNullOrBlank()) {
                    val r = row(id)
                    if (r.tokenAt == null && event.token.isNotEmpty()) r.tokenAt = event.timestamp
                    r.state = "GENERATING"
                    changed = r
                }
            }
            is CognitiveCompleted -> {
                val id = event.interactionId ?: requests[event.requestId]
                if (!id.isNullOrBlank()) {
                    val r = row(id)
                    r.tokenAt = r.tokenAt ?: event.metrics?.firstTokenAtMs
                    changed = r
                }
                requests.remove(event.requestId)
            }
            is CognitiveFailed -> {
                val id = event.interactionId ?: requests[event.requestId]
                if (!id.isNullOrBlank()) {
                    val r = row(id)
                    r.state = "GENERATION_FAILED"
                    changed = r
                }
                requests.remove(event.requestId)
            }
            is WorkloadStageEvent -> {
                val r = row(event.interactionId)
                if (event.state in setOf(WorkloadStageState.COMPLETED, WorkloadStageState.FAILED,
                        WorkloadStageState.CANCELLED, WorkloadStageState.SKIPPED)) {
                    event.durationMs?.let { r.stages[event.workload + "/" + event.stageId] = it }
                }
                changed = r
            }
            is TtsRequested -> {
                bind(event.interactionId ?: event.transactionId, event.sourceCaptureId)
                val r = row(event.interactionId ?: event.sourceCaptureId ?: event.transactionId)
                if (r.outputTransactionId != event.transactionId) {
                    r.outputTransactionId = event.transactionId
                    r.playbackAt = null
                }
                if (r.playbackAt == null) r.state = "SPEECH_PREPARING"
                changed = r
            }
            is TtsStarted -> {
                val r = row(event.interactionId ?: event.transactionId)
                if (r.outputTransactionId == null) r.outputTransactionId = event.transactionId
                // An obsolete playback owner must not replace a newer response.
                if (r.outputTransactionId == event.transactionId) {
                    if (r.playbackAt == null) r.playbackAt = event.timestamp
                    r.state = "SPEAKING"
                    changed = r
                }
            }
            is TtsCompleted -> {
                val r = row(event.interactionId ?: event.transactionId)
                if (r.outputTransactionId == null) r.outputTransactionId = event.transactionId
                if (r.outputTransactionId == event.transactionId) {
                    r.state = "COMPLETED"
                    changed = r
                }
            }
            is TtsFailed -> {
                val r = row(event.interactionId ?: event.transactionId)
                if (r.outputTransactionId == null) r.outputTransactionId = event.transactionId
                if (r.outputTransactionId == event.transactionId) {
                    r.state = "OUTPUT_FAILED"
                    changed = r
                }
            }
            is TtsCancelled -> {
                val r = row(event.interactionId ?: event.transactionId)
                if (r.outputTransactionId == null) r.outputTransactionId = event.transactionId
                if (r.outputTransactionId == event.transactionId) {
                    r.state = "CANCELLED"
                    changed = r
                }
            }
            else -> Unit
        }
        // Hard bounds: eviction includes map aliases/request identities so an old
        // response cannot populate a newly created trace or keep memory resident.
        while (rows.size > capacity) rows.remove(rows.keys.first())
        val live = rows.keys
        aliases.entries.removeAll { (_, target) -> target !in live }
        while (aliases.size > capacity) aliases.remove(aliases.keys.first())
        while (requests.size > capacity * 2) requests.remove(requests.keys.first())
        if (changed != null) mutable.value = rows.values.map { it.snapshot() }
        return changed?.snapshot()
    }
}
