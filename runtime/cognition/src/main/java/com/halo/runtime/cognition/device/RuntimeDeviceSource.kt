package com.halo.runtime.cognition.device

import kotlinx.coroutines.flow.StateFlow

interface RuntimeDeviceSource {
    val devices: StateFlow<List<RuntimeDevice>>
}
