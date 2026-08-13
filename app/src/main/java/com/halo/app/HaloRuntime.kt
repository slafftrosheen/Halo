package com.halo.app

import com.halo.app.di.ManualDependencyGraph
import com.halo.sdk.host.ConnectionState

/**
 * Owns the application-level runtime lifecycle.
 *
 * Construction != startup.
 */
class HaloRuntime(
    private val graph: ManualDependencyGraph
) {

    private var started = false
    fun isStarted() = started

    suspend fun start() {

        check(!started) {
            "HaloRuntime is already running"
        }

        check(
            graph.wearableHost.connectivity.value
                is ConnectionState.Connected
        ) {
            "Runtime requires a connected host; discovery and physical device selection are separate workflows"
        }

        /*
         * Cognitive engines
         */
        graph.interpretationEngine.initialize(
            graph.engineContext
        )

        graph.reasoningEngine.initialize(
            graph.engineContext
        )

        graph.interpretationEngine.start()
        graph.reasoningEngine.start()

        /*
         * Planning/execution orchestration
         */
        graph.runtimeOrchestrator.start()

        /*
         * Audio perception
         */
        graph.audioPerceptionEngine.initialize(
            graph.engineContext
        )

        graph.audioPerceptionEngine.start()

        /*
         * AI Handoff
         */
        graph.aiHandoffOrchestrator.start()

        /*
         * Kernel
         */
        graph.microkernel.boot()

        started = true
    }

    suspend fun stop() {

        if (!started) {
            return
        }

        /*
         * Stop producers first.
         */
        graph.audioPerceptionEngine.stop()

        /*
         * Stop orchestration.
         */
        graph.runtimeOrchestrator.stop()

        /*
         * Stop cognition.
         */
        graph.reasoningEngine.stop()
        graph.reasoningEngine.dispose()

        graph.interpretationEngine.stop()
        graph.interpretationEngine.dispose()

        /*
         * Stop kernel.
         */
        graph.microkernel.shutdown()

        /*
         * Disconnect physical host last.
         */
        graph.wearableHost.disconnect()

        started = false
    }
}