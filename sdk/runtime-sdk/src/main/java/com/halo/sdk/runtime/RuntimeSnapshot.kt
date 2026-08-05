package com.halo.sdk.runtime
import java.time.Duration
import java.time.Instant

data class RuntimeSnapshot(
    val runtimeState: RuntimeState,
    val uptime: Duration,
    val engines: List<EngineSnapshot>,
    val services: List<ServiceSnapshot>,
    val plugins: List<PluginSnapshot>,
    val queueDepth: Int,
    val activeTasks: Int
)

data class EngineSnapshot(
    val id: String,
    val state: EngineState,
    val health: EngineHealth,
    val uptime: Duration,
    val eventsProcessed: Long,
    val lastActivity: Instant
)

data class ServiceSnapshot(
    val id: String,
    val status: String
)

data class PluginSnapshot(
    val id: String,
    val status: String
)
