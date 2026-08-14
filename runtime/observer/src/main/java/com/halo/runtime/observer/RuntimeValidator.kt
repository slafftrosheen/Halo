package com.halo.runtime.observer

import com.halo.sdk.runtime.ObservationTrace
import com.halo.sdk.runtime.RuntimeStage
import com.halo.sdk.runtime.HaloEvent

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Warning(val message: String) : ValidationResult
    data class Error(val message: String) : ValidationResult
}

class RuntimeValidator {
    
    fun validate(trace: ObservationTrace): ValidationResult {
        val stages = trace.stages
        if (stages.isEmpty()) return ValidationResult.Valid

        // 1. Duplicate stages
        val stageCounts = stages.groupingBy { it.stage }.eachCount()
        val duplicates = stageCounts.filter { it.value > 1 }
        if (duplicates.isNotEmpty()) {
            return ValidationResult.Error("Duplicate stages detected in trace ${trace.observationId}: ${duplicates.keys}")
        }

        // 2. Stage Ordering & Impossible Transitions
        val expectedOrder = listOf(
            RuntimeStage.INTERPRETATION,
            RuntimeStage.GOAL_GENERATION,
            RuntimeStage.PLANNING,
            RuntimeStage.EXECUTION,
            RuntimeStage.REFLECTION
        )
        
        var lastIndex = -1
        for (stageMetric in stages) {
            val currentIndex = expectedOrder.indexOf(stageMetric.stage)
            
            // It must be strictly sequential (no jumping backward or skipping required stages without failing)
            if (lastIndex != -1 && currentIndex != lastIndex + 1) {
                return ValidationResult.Error("Impossible transition in trace ${trace.observationId}: jumped from ${expectedOrder[lastIndex]} to ${stageMetric.stage}")
            }
            lastIndex = currentIndex
        }

        // 3. Latency anomalies
        for (stageMetric in stages) {
            val threshold = if (stageMetric.stage == RuntimeStage.EXECUTION) 30000 else 5000
            if (stageMetric.latency > threshold) { 
                return ValidationResult.Warning("Latency anomaly in trace ${trace.observationId}: ${stageMetric.stage} took ${stageMetric.latency}ms")
            }
        }

        // 4. Missing stages (only validated when trace is completed successfully)
        val hasReflection = stages.any { it.stage == RuntimeStage.REFLECTION }
        val hasError = stages.any { !it.success }
        if (hasReflection && !hasError) {
            val missing = expectedOrder.filter { expected ->
                stages.none { it.stage == expected }
            }
            if (missing.isNotEmpty()) {
                return ValidationResult.Error("Missing stages in completed trace ${trace.observationId}: $missing")
            }
        }
        
        return ValidationResult.Valid
    }

    private val intentStates = mutableMapOf<String, String>()
    private val terminalStates = setOf("SUCCEEDED", "FAILED", "DENIED", "REJECTED")

    fun validateSemanticEvent(event: HaloEvent): ValidationResult {
        when (event) {
            is com.halo.sdk.runtime.IntentCreated -> {
                intentStates[event.intentId] = "CREATED"
            }
            is com.halo.sdk.runtime.IntentDispatched -> {
                val currentState = intentStates[event.intentId]
                if (currentState == null) {
                    return ValidationResult.Error("IntentDispatched for unknown intent: ${event.intentId}")
                }
                intentStates[event.intentId] = "DISPATCHED"
            }
            is com.halo.sdk.runtime.EmbodimentExecutionEvent -> {
                val intentId = event.result.intentId
                val currentState = intentStates[intentId]
                val incomingState = event.result.status.name
                
                if (currentState == null) {
                    return ValidationResult.Error("ExecutionResult for unknown intent: $intentId")
                }
                if (terminalStates.contains(currentState)) {
                    return ValidationResult.Error("Duplicate terminal state for intent $intentId: $incomingState after $currentState")
                }
                if (currentState != "DISPATCHED" && currentState != "CREATED") {
                    return ValidationResult.Error("ExecutionResult for intent $intentId without preceding creation/dispatch. Current: $currentState")
                }
                
                if (terminalStates.contains(incomingState)) {
                    intentStates[intentId] = incomingState
                }
            }
        }
        return ValidationResult.Valid
    }
}
