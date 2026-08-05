package com.halo.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.di.ManualDependencyGraph
import com.halo.sdk.runtime.RuntimeSnapshot
import com.halo.sdk.runtime.RuntimeState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DeveloperConsoleViewModel : ViewModel() {
    
    val uiState: StateFlow<RuntimeSnapshot> = MutableStateFlow(
        RuntimeSnapshot(
            runtimeState = com.halo.sdk.runtime.RuntimeState.Running,
            uptime = java.time.Duration.ZERO,
            engines = listOf(
                com.halo.sdk.runtime.EngineSnapshot("com.halo.engines.perception", com.halo.sdk.runtime.EngineState.Running, com.halo.sdk.runtime.EngineHealth.Healthy, java.time.Duration.ZERO, 0, java.time.Instant.now()),
                com.halo.sdk.runtime.EngineSnapshot("com.halo.engines.speech", com.halo.sdk.runtime.EngineState.Running, com.halo.sdk.runtime.EngineHealth.Healthy, java.time.Duration.ZERO, 0, java.time.Instant.now()),
                com.halo.sdk.runtime.EngineSnapshot("com.halo.engines.reasoning", com.halo.sdk.runtime.EngineState.Running, com.halo.sdk.runtime.EngineHealth.Healthy, java.time.Duration.ZERO, 0, java.time.Instant.now()),
                com.halo.sdk.runtime.EngineSnapshot("com.halo.engines.planning", com.halo.sdk.runtime.EngineState.Running, com.halo.sdk.runtime.EngineHealth.Healthy, java.time.Duration.ZERO, 0, java.time.Instant.now()),
                com.halo.sdk.runtime.EngineSnapshot("com.halo.engines.execution", com.halo.sdk.runtime.EngineState.Running, com.halo.sdk.runtime.EngineHealth.Healthy, java.time.Duration.ZERO, 0, java.time.Instant.now()),
                com.halo.sdk.runtime.EngineSnapshot("com.halo.engines.memory", com.halo.sdk.runtime.EngineState.Running, com.halo.sdk.runtime.EngineHealth.Healthy, java.time.Duration.ZERO, 0, java.time.Instant.now()),
            ),
            services = emptyList(),
            plugins = emptyList(),
            queueDepth = 0,
            activeTasks = 0
        )
    )
    
}
