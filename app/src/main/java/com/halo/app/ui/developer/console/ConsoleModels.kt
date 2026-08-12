package com.halo.app.ui.developer.console

data class EngineStatus(
    val name: String,
    val isHealthy: Boolean,
    val latencyMs: Long?,
    val queueSize: Int
)

data class ConsoleUiState(
    val selectedTab: Int = 0,
    val uptime: String = "00:00:00",
    val memoryUsage: String = "0 MB",
    val threadCount: Int = 0,
    val engines: List<EngineStatus> = emptyList()
)
