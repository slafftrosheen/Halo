package com.halo.engines.learning.service

import com.halo.runtime.cognition.memory.Memory
import com.halo.runtime.cognition.learning.ExecutionResult

class PatternExtractor {
    fun extractFailures(results: List<ExecutionResult>): List<String> {
        // Look for repeated errors or high latency patterns
        return results.filter { !it.success }.mapNotNull { it.error }
    }
}

