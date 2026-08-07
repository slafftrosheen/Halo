package com.halo.runtime.observer

import com.halo.sdk.runtime.ObservationTrace
import com.halo.sdk.runtime.RuntimeStage

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
}
