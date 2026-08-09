package com.halo.app

import com.halo.app.di.ManualDependencyGraph
import com.halo.sdk.host.ConnectionState

/**
 * Orchestrates the startup and shutdown sequence for the entire Halo system.
 */
class HaloRuntime(
    private val graph: ManualDependencyGraph
) {
    suspend fun start() {
        check(graph.wearableHost.connectivity.value is ConnectionState.Connected) {
            "Runtime requires a connected host; discovery and physical device selection are separate workflows"
        }

        // Initialize audio subsystem
        val audioEngine = graph.audioPerceptionEngine
        audioEngine.initialize(graph.engineContext)
        audioEngine.start()

        // Wake up the cognitive engine
        graph.microkernel.boot()
    }

    suspend fun stop() {
        graph.microkernel.shutdown()
        graph.audioPerceptionEngine.stop()
        graph.wearableHost.disconnect()
    }
}
