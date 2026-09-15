package com.halo.app.ui.developer.console

import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.sdk.runtime.NativeResourceSnapshot

data class EngineStatus(
    val name: String,
    val isHealthy: Boolean,
    val latencyMs: Long?,
    val queueSize: Int
)

data class ProviderStatusRow(
    val id: String,
    val name: String,
    val status: String,
    val activeModel: String?
)

data class ConsoleTimelineRow(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val timestampMs: Long
)

data class ConsoleUiState(
    val uptime: String = "00:00:00",
    val memoryUsage: String = "0 MB",
    val threadCount: Int = 0,
    val engines: List<EngineStatus> = emptyList(),
    val logs: List<String> = emptyList(),
    val blackboardFacts: List<String> = emptyList(),
    val providers: List<ProviderStatusRow> = emptyList(),
    val events: List<ConsoleTimelineRow> = emptyList(),
    val nativeResources: List<NativeResourceSnapshot> = emptyList(),
    val presence: HaloRuntimeState = HaloRuntimeState.Idle,
    val runtimeState: String = "",
    val healthy: Boolean = false,
    val degraded: Boolean = false
)
