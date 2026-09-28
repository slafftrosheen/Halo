package com.halo.runtime.observer

import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.InferenceObserved
import com.halo.sdk.runtime.CognitiveFailed
import com.halo.sdk.runtime.ToolCallCompleted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InferenceAggregate(
    val requests: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val failures: Long = 0,
    val totalDurationMs: Long = 0,
    val routerResolutions: Map<String, Long> = emptyMap(),
)

data class ToolAggregate(
    val outcomes: Long = 0,
    val failures: Long = 0,
    val unknownOutcomes: Long = 0,
    val totalDurationMs: Long = 0,
    val outcomesByToolId: Map<String, Long> = emptyMap(),
    val failuresByCode: Map<String, Long> = emptyMap(),
)

data class InferenceExecutionSnapshot(
    val requestId: String,
    val providerId: String,
    val modelId: String?,
    val success: Boolean,
    val backend: String? = null,
    val latencyMs: Long? = null,
    val failureStage: String? = null,
    val failureType: String? = null,
    val recordedAtMs: Long,
)

/** Bounded, content-free operational telemetry projection. */
class InferenceLedger(eventBus: EventBus, scope: CoroutineScope) {
    private val _aggregate = MutableStateFlow(InferenceAggregate())
    val aggregate: StateFlow<InferenceAggregate> = _aggregate.asStateFlow()
    private val _toolAggregate = MutableStateFlow(ToolAggregate())
    val toolAggregate: StateFlow<ToolAggregate> = _toolAggregate.asStateFlow()
    private val _lastExecution = MutableStateFlow<InferenceExecutionSnapshot?>(null)
    val lastExecution: StateFlow<InferenceExecutionSnapshot?> = _lastExecution.asStateFlow()

    init {
        scope.launch {
            eventBus.events().collect { event ->
                when (event) {
                    is InferenceObserved -> {
                        val current = _aggregate.value
                        val resolved = event.resolvedModelId?.takeIf { it != event.requestedModelId }
                        _aggregate.value = current.copy(
                            requests = current.requests + 1,
                            inputTokens = current.inputTokens + (event.metrics?.inputTokens ?: 0),
                            outputTokens = current.outputTokens + (event.metrics?.outputTokens ?: 0),
                            failures = current.failures + if (event.success) 0 else 1,
                            totalDurationMs = current.totalDurationMs + (event.metrics?.totalDurationMs ?: 0L),
                            routerResolutions = if (resolved == null) current.routerResolutions else
                                current.routerResolutions.incrementBounded(resolved),
                        )
                        val priorFailure = _lastExecution.value
                            ?.takeIf { !event.success && it.requestId == event.requestId }
                        _lastExecution.value = InferenceExecutionSnapshot(
                            requestId = event.requestId,
                            providerId = event.providerId,
                            modelId = event.resolvedModelId ?: event.requestedModelId,
                            success = event.success,
                            backend = event.backend,
                            latencyMs = event.metrics?.totalDurationMs,
                            failureStage = priorFailure?.failureStage,
                            failureType = priorFailure?.failureType,
                            recordedAtMs = event.timestamp,
                        )
                    }
                    is CognitiveFailed -> event.providerId?.let { providerId ->
                        _lastExecution.value = InferenceExecutionSnapshot(
                            requestId = event.requestId,
                            providerId = providerId,
                            modelId = event.modelId,
                            success = false,
                            failureStage = event.stage,
                            failureType = event.type,
                            recordedAtMs = event.timestamp,
                        )
                    }
                    is ToolCallCompleted -> {
                        val current = _toolAggregate.value
                        val failureCode = event.failureCode?.takeIf { !event.success }
                        _toolAggregate.value = current.copy(
                            outcomes = current.outcomes + 1,
                            failures = current.failures + if (event.success) 0 else 1,
                            unknownOutcomes = current.unknownOutcomes + if (event.actionTruth == "UNKNOWN") 1 else 0,
                            totalDurationMs = current.totalDurationMs + (event.durationMs ?: 0L),
                            outcomesByToolId = current.outcomesByToolId.incrementBounded(event.toolId),
                            failuresByCode = if (failureCode == null) current.failuresByCode else
                                current.failuresByCode.incrementBounded(failureCode),
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun Map<String, Long>.incrementBounded(key: String, maxKeys: Int = 128): Map<String, Long> {
        if (key !in this && size >= maxKeys) return this
        return this + (key to ((this[key] ?: 0L) + 1L))
    }
}
