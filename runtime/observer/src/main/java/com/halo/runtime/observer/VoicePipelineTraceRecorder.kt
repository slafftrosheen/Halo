package com.halo.runtime.observer

import com.halo.sdk.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class VoicePipelineStage {
    CAPTURE_STARTED, END_OF_SPEECH, PARTIAL_INTENT, TRANSCRIPT_READY, REQUEST_STARTED,
    CONTEXT_READY, GENERATION_STARTED, FIRST_TOKEN, RESPONSE_FINISHED,
    SYNTHESIS_REQUESTED, FIRST_AUDIO, OUTPUT_FINISHED, CANCELLED, FAILED,
}

data class VoicePipelinePoint(val stage: VoicePipelineStage, val operationId: String, val atMs: Long)

/** No transcript, generated content, tool arguments or failure messages enter this snapshot. */
data class VoicePipelineTrace(
    val captureId: String,
    val interactionId: String,
    val points: List<VoicePipelinePoint>,
    val providerId: String? = null,
    val modelId: String? = null,
    val backend: String? = null,
    val truncated: Boolean = false,
) {
    fun first(stage: VoicePipelineStage): Long? = points.filter { it.stage == stage }.minOfOrNull { it.atMs }

    val endOfSpeechToFirstAudioMs: Long? get() {
        val endpoint = first(VoicePipelineStage.END_OF_SPEECH) ?: return null
        val audio = first(VoicePipelineStage.FIRST_AUDIO) ?: return null
        val start = first(VoicePipelineStage.CAPTURE_STARTED) ?: return null
        return (audio - endpoint).takeIf { !truncated && endpoint >= start && audio >= endpoint }
    }

    /** Union of real generation/playback intersections; missing ends are not zero latency. */
    val generationAudioOverlapMs: Long? get() {
        if (truncated) return null
        fun intervals(start: VoicePipelineStage, end: VoicePipelineStage): List<Pair<Long, Long>> =
            points.filter { it.stage == start }.mapNotNull { begin ->
                points.firstOrNull { it.stage == end && it.operationId == begin.operationId }
                    ?.atMs?.takeIf { it >= begin.atMs }?.let { begin.atMs to it }
            }
        val generation = intervals(VoicePipelineStage.GENERATION_STARTED, VoicePipelineStage.RESPONSE_FINISHED)
        val audio = intervals(VoicePipelineStage.FIRST_AUDIO, VoicePipelineStage.OUTPUT_FINISHED)
        if (generation.isEmpty() || audio.isEmpty()) return null
        val overlaps = generation.flatMap { g -> audio.mapNotNull { a ->
            val from = maxOf(g.first, a.first); val to = minOf(g.second, a.second)
            if (to > from) from to to else null
        } }.sortedBy { it.first }
        var total = 0L; var until = Long.MIN_VALUE
        overlaps.forEach { (from, to) ->
            total += (to - maxOf(from, until)).coerceAtLeast(0)
            until = maxOf(until, to)
        }
        return total
    }
}

/** Single-consumer projection. All identity maps share the same bounded trace lifetime. */
class VoicePipelineTraceStore(private val maxTraces: Int = 32, private val maxPoints: Int = 128) {
    init { require(maxTraces in 1..128); require(maxPoints in 4..512) }
    private val traces = LinkedHashMap<String, VoicePipelineTrace>()
    private val requests = mutableMapOf<String, String>()
    private val outputs = mutableMapOf<String, String>()
    private val interactions = mutableMapOf<String, String>()
    private val bindingLimit = maxTraces * maxPoints

    fun snapshots(): List<VoicePipelineTrace> = traces.values.toList()

    private fun point(capture: String, stage: VoicePipelineStage, operation: String, at: Long): Boolean {
        val trace = traces[capture] ?: return false
        if (trace.points.any { it.stage == stage && it.operationId == operation }) return false
        if (trace.points.size >= maxPoints) {
            if (trace.truncated) return false
            traces[capture] = trace.copy(truncated = true)
            return true
        }
        traces[capture] = trace.copy(points = trace.points + VoicePipelinePoint(stage, operation, at))
        return true
    }

    private fun bind(map: MutableMap<String, String>, key: String, capture: String): Boolean {
        val previous = map[key]
        if (previous != null) return previous == capture
        if (map.size >= bindingLimit || traces[capture]?.truncated == true) return false
        // Per-trace bounds prevent one unfinished turn consuming all alias storage.
        if (map.values.count { it == capture } >= maxPoints) return false
        map[key] = capture
        return true
    }

    private fun captureForInteraction(id: String?): String? = id?.let { interactions[it] ?: it.takeIf(traces::containsKey) }

    fun accept(event: HaloEvent): Boolean {
        when (event) {
            is InteractionLatencyUpdated -> {
                val timing = event.trace
                val capture = timing.interactionId
                if (!traces.containsKey(capture)) {
                    while (traces.size >= maxTraces) {
                        val oldest = traces.keys.first(); traces.remove(oldest)
                        requests.entries.removeAll { it.value == oldest }
                        outputs.entries.removeAll { it.value == oldest }
                        interactions.entries.removeAll { it.value == oldest }
                    }
                    traces[capture] = VoicePipelineTrace(capture, capture, emptyList())
                    interactions[capture] = capture
                }
                var changed = point(capture, VoicePipelineStage.CAPTURE_STARTED, capture, timing.startedAtMs)
                timing.speechEndedAtMs?.let { changed = point(capture, VoicePipelineStage.END_OF_SPEECH, capture, it) || changed }
                return changed
            }
            is SpeechRecognized -> return point(event.input?.captureId ?: event.id,
                VoicePipelineStage.TRANSCRIPT_READY, event.input?.captureId ?: event.id, event.timestamp)
            is SpeechProvisionalIntentUpdated -> return if (event.finalInputReceived) false
                else point(event.captureId, VoicePipelineStage.PARTIAL_INTENT, event.captureId, event.timestamp)
            is CognitiveRequestStarted -> {
                val capture = captureForInteraction(event.interactionId) ?: return false
                if (!bind(requests, event.requestId, capture)) return false
                return point(capture, VoicePipelineStage.REQUEST_STARTED, event.requestId, event.timestamp)
            }
            is StreamingSpeechPrepared -> {
                val capture = event.sourceCaptureId?.takeIf(traces::containsKey) ?: return false
                if (!bind(requests, event.requestId, capture) || !bind(interactions, event.interactionId, capture)) return false
                val previous = traces.getValue(capture)
                if (previous.interactionId == event.interactionId) return false
                traces[capture] = previous.copy(interactionId = event.interactionId)
                return true
            }
            is TtsRequested -> {
                val capture = event.sourceCaptureId?.takeIf(traces::containsKey)
                    ?: captureForInteraction(event.interactionId) ?: return false
                if (!bind(outputs, event.transactionId, capture)) return false
                return point(capture, VoicePipelineStage.SYNTHESIS_REQUESTED, event.transactionId, event.timestamp)
            }
            is CognitiveReasoningStarted -> {
                val capture = requests[event.requestId] ?: return false
                val trace = traces.getValue(capture)
                traces[capture] = trace.copy(providerId = event.providerId, modelId = event.modelId)
                return point(capture, VoicePipelineStage.GENERATION_STARTED, event.requestId, event.timestamp)
            }
            is InferenceObserved -> {
                val capture = requests[event.requestId] ?: return false
                val trace = traces.getValue(capture)
                traces[capture] = trace.copy(providerId = event.providerId,
                    modelId = event.resolvedModelId ?: event.requestedModelId, backend = event.backend)
                return true
            }
            is CognitiveTokenReceived -> return requests[event.requestId]?.let {
                point(it, VoicePipelineStage.FIRST_TOKEN, event.requestId, event.timestamp) } ?: false
            is ContextAssembled -> return requests[event.requestId]?.let {
                point(it, VoicePipelineStage.CONTEXT_READY, event.requestId, event.timestamp) } ?: false
            is CognitiveCompleted -> return requests[event.requestId]?.let {
                point(it, VoicePipelineStage.RESPONSE_FINISHED, event.requestId, event.timestamp) } ?: false
            is CognitiveFailed -> return requests[event.requestId]?.let {
                point(it, VoicePipelineStage.FAILED, event.requestId, event.timestamp) } ?: false
            is TtsStarted -> return outputs[event.transactionId]?.let {
                point(it, VoicePipelineStage.FIRST_AUDIO, event.transactionId, event.timestamp) } ?: false
            is TtsCompleted -> return outputs[event.transactionId]?.let {
                point(it, VoicePipelineStage.OUTPUT_FINISHED, event.transactionId, event.timestamp) } ?: false
            is TtsCancelled -> return outputs[event.transactionId]?.let {
                point(it, VoicePipelineStage.CANCELLED, event.transactionId, event.timestamp) } ?: false
            is TtsFailed -> return outputs[event.transactionId]?.let {
                point(it, VoicePipelineStage.FAILED, event.transactionId, event.timestamp) } ?: false
            is SpeechRejected -> return event.captureId?.let {
                point(it, if (event.reason == "CANCELLED") VoicePipelineStage.CANCELLED else VoicePipelineStage.FAILED,
                    it, event.timestamp) } ?: false
            else -> return false
        }
    }

    fun report(): String = voicePipelineReport(snapshots())
}

fun voicePipelineReport(traces: List<VoicePipelineTrace>): String = buildString {
    append("Voice pipeline trace v1 (event wall-clock ms; process only)\n")
    append("CPU/GPU utilization, placement, energy, thermal: NOT_MEASURED\n")
    if (traces.isEmpty()) append("Voice latency: NOT_MEASURED\n")
    fun identity(value: String?) = value?.take(160)?.replace(Regex("[\\r\\n\\t]"), "_") ?: "NOT_MEASURED"
    traces.forEach { trace ->
        append("capture=${identity(trace.captureId)} interaction=${identity(trace.interactionId)} ")
        append("provider=${identity(trace.providerId)} model=${identity(trace.modelId)} backend=${identity(trace.backend)} ")
        append("endOfSpeechToFirstAudioMs=${trace.endOfSpeechToFirstAudioMs ?: "NOT_MEASURED"} ")
        append("generationAudioOverlapMs=${trace.generationAudioOverlapMs ?: "NOT_MEASURED"} truncated=${trace.truncated}\n")
        trace.points.forEach { append("  ${it.stage} operation=${identity(it.operationId)} atMs=${it.atMs}\n") }
    }
}

/** Starts before producers; token floods do not allocate new snapshots after the first token. */
class VoicePipelineTraceRecorder(eventBus: EventBus, scope: CoroutineScope) {
    private val store = VoicePipelineTraceStore()
    private val mutable = MutableStateFlow<List<VoicePipelineTrace>>(emptyList())
    val traces = mutable.asStateFlow()
    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            eventBus.events().collect { if (store.accept(it)) mutable.value = store.snapshots() }
        }
    }
    fun report(): String = voicePipelineReport(traces.value)
}
