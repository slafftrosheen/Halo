package com.halo.app

import com.halo.app.di.ManualDependencyGraph

/**
 * Orchestrates the startup and shutdown sequence for the entire Halo system.
 */
class HaloRuntime(
    private val graph: ManualDependencyGraph
) {
    suspend fun start() {
        // Connect to wearable device (e.g. HeyCyan Glasses)
        graph.wearableHost.connect()

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
