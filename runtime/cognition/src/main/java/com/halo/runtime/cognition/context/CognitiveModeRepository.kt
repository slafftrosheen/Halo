package com.halo.runtime.cognition.context

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface CognitiveModeRepository {
    val mode: StateFlow<CognitiveOperatingMode>
    suspend fun setMode(mode: CognitiveOperatingMode)
}

class InMemoryCognitiveModeRepository(
    initialMode: CognitiveOperatingMode = CognitiveOperatingMode.AUTO
) : CognitiveModeRepository {
    private val _mode = MutableStateFlow(initialMode)
    override val mode: StateFlow<CognitiveOperatingMode> = _mode.asStateFlow()

    override suspend fun setMode(mode: CognitiveOperatingMode) {
        _mode.value = mode
    }
}
