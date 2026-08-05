package com.halo.runtime.kernel

import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.StateFlow

class HaloRuntime(
    engines: List<Engine>,
    runtimeContext: RuntimeContext
) : Runtime {

    private val kernel = RuntimeKernel(engines, runtimeContext)

    override val state: StateFlow<RuntimeState> = kernel.state

    override suspend fun boot() {
        kernel.boot()
    }

    override suspend fun shutdown() {
        kernel.shutdown()
    }
}
