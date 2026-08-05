package com.halo.runtime.observatory

import kotlinx.coroutines.flow.Flow

/**
 * The Runtime Observatory provides real-time metrics and diagnostics
 * for the HAL Runtime Kernel.
 */
interface Observatory {
    val metrics: Flow<RuntimeMetrics>
    
    fun recordEvent(eventName: String)
    fun recordLatency(operation: String, durationMs: Long)
    fun updateMemoryUsage(bytes: Long)
    fun updateBatteryImpact(drainRate: Float)
}

data class RuntimeMetrics(
    val eventsPerSecond: Int,
    val commandsPerSecond: Int,
    val queriesPerSecond: Int,
    val memoryUsageMb: Float,
    val batteryDrainRate: Float,
    val activeServices: Int
)
