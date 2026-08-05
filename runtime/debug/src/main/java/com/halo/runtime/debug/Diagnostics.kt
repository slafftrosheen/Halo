package com.halo.runtime.debug

data class RuntimeMetrics(
    val activeEngines: Int,
    val memoryUsage: Long,
    val providerLatency: Long,
    val queueDepth: Int
)

interface EngineInspector {
    fun inspectMetrics(): RuntimeMetrics
}

interface EventTracer {
    fun trace(event: com.halo.sdk.runtime.HaloEvent)
}

interface Logger {
    fun log(message: String)
}
