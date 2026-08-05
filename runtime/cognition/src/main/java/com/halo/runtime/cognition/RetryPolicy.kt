package com.halo.runtime.cognition

data class RetryPolicy(
    val maxRetries: Int = 0,
    val backoffMultiplier: Float = 1.0f,
    val initialDelayMs: Long = 0L,
    val maxDelayMs: Long = 0L
)

data class ExecutionMetrics(
    val latencyMs: Long,
    val failures: Int,
    val retries: Int,
    val parallelTasks: Int,
    val rollbackCount: Int
)
