package com.halo.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.di.ManualDependencyGraph
import com.halo.sdk.runtime.RuntimeSnapshot
import com.halo.sdk.runtime.RuntimeState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DeveloperConsoleViewModel : ViewModel() {
    
    private val runtimeMonitor = ManualDependencyGraph.runtimeMonitor
    
    val uiState: StateFlow<RuntimeSnapshot> = runtimeMonitor.snapshots.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = runtimeMonitor.snapshots.value
    )
    
}
