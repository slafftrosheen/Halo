package com.halo.runtime.observer

import com.halo.sdk.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class TraceRecorder(
    private val eventBus: EventBus,
    private val validator: RuntimeValidator,
    private val scope: CoroutineScope
) {
    companion object {
        const val MAX_TRACES = 20
    }

    private val _tracesFlow = MutableStateFlow<List<ObservationTrace>>(emptyList())
    val tracesFlow: StateFlow<List<ObservationTrace>> = _tracesFlow.asStateFlow()

    init {
        val deque = ArrayDeque<ObservationTrace>(MAX_TRACES)
        scope.launch {
            eventBus.observe(StageMetricsPublished::class.java).collect { event ->
                val metric = StageMetrics(
                    stage = event.stage,
                    startedAt = event.startedAt,
                    finishedAt = event.finishedAt,
                    latency = event.latency,
                    success = event.success,
                    error = event.error
                )
                
                val existingTrace = deque.find { it.observationId == event.observationId }
                if (existingTrace != null) {
                    val updatedStages = existingTrace.stages + metric
                    val startedAt = updatedStages.minOf { it.startedAt }
                    val finishedAt = updatedStages.maxOf { it.finishedAt }
                    val totalLatency = finishedAt - startedAt
                    
                    val traceState = if (!metric.success) TraceState.FAILED else if (metric.stage == com.halo.sdk.runtime.RuntimeStage.REFLECTION) TraceState.COMPLETED else TraceState.RUNNING
                    
                    var updatedTrace = existingTrace.copy(
                        stages = updatedStages,
                        startedAt = startedAt,
                        finishedAt = finishedAt,
                        totalLatency = totalLatency,
                        state = traceState,
                        validation = TraceValidation.PENDING
                    )
                    
                    val result = validator.validate(updatedTrace)
                    updatedTrace = updatedTrace.copy(
                        validation = if (result is ValidationResult.Error) TraceValidation.INVALID else TraceValidation.VALID
                    )
                    
                    deque.remove(existingTrace)
                    deque.addLast(updatedTrace)
                    
                    when (result) {
                        is ValidationResult.Error -> eventBus.publish(TraceValidationFailed(observationId = updatedTrace.observationId, message = result.message))
                        is ValidationResult.Warning -> eventBus.publish(TraceValidationWarning(observationId = updatedTrace.observationId, message = result.message))
                        is ValidationResult.Valid -> {}
                    }
                } else {
                    if (deque.size >= MAX_TRACES) {
                        deque.removeFirst()
                    }
                    val traceState = if (!metric.success) TraceState.FAILED else if (metric.stage == com.halo.sdk.runtime.RuntimeStage.REFLECTION) TraceState.COMPLETED else TraceState.RUNNING
                    
                    var newTrace = ObservationTrace(
                        observationId = event.observationId,
                        startedAt = metric.startedAt,
                        finishedAt = metric.finishedAt,
                        totalLatency = metric.latency,
                        state = traceState,
                        validation = TraceValidation.PENDING,
                        stages = listOf(metric)
                    )
                    
                    val result = validator.validate(newTrace)
                    newTrace = newTrace.copy(
                        validation = if (result is ValidationResult.Error) TraceValidation.INVALID else TraceValidation.VALID
                    )
                    
                    deque.addLast(newTrace)
                    
                    when (result) {
                        is ValidationResult.Error -> eventBus.publish(TraceValidationFailed(observationId = newTrace.observationId, message = result.message))
                        is ValidationResult.Warning -> eventBus.publish(TraceValidationWarning(observationId = newTrace.observationId, message = result.message))
                        is ValidationResult.Valid -> {}
                    }
                }
                
                _tracesFlow.value = deque.toList()
            }
        }
        
        scope.launch {
            eventBus.observe(HaloEvent::class.java).collect { event ->
                val result = validator.validateSemanticEvent(event)
                when (result) {
                    is ValidationResult.Error -> eventBus.publish(TraceValidationFailed(observationId = event.id, message = result.message))
                    is ValidationResult.Warning -> eventBus.publish(TraceValidationWarning(observationId = event.id, message = result.message))
                    is ValidationResult.Valid -> {}
                }
            }
        }
    }
}
