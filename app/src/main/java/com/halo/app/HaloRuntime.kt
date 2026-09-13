package com.halo.app

import com.halo.app.di.ManualDependencyGraph
import com.halo.sdk.host.ConnectionState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the application-level runtime lifecycle.
 *
 * Construction != startup.
 */
class HaloRuntime(
    private val graph: ManualDependencyGraph
) {

    private val lifecycleMutex = Mutex()
    private var started = false

    fun isStarted() = started

    suspend fun start() = lifecycleMutex.withLock {
        check(!started) {
            "HaloRuntime is already running"
        }

        val connectivity = graph.wearableHost.connectivity.value
        check(
            connectivity is ConnectionState.Connected || connectivity is ConnectionState.Ready
        ) {
            "Runtime requires a connected host; discovery and physical device selection are separate workflows"
        }

        var interpretationInitialized = false
        var interpretationStarted = false
        var reasoningInitialized = false
        var reasoningStarted = false
        var planningInitialized = false
        var planningStarted = false
        var executionInitialized = false
        var executionStarted = false
        var audioInitialized = false
        var audioStarted = false
        var kernelBooted = false

        try {
            /*
             * Cognitive engines
             */
            graph.interpretationEngine.initialize(
                graph.engineContext
            )
            interpretationInitialized = true

            graph.reasoningEngine.initialize(
                graph.engineContext
            )
            reasoningInitialized = true

            checkHostStillConnected()

            graph.interpretationEngine.start()
            interpretationStarted = true
            graph.reasoningEngine.start()
            reasoningStarted = true

            /*
             * Planning and execution engines
             */
            graph.planningEngine.initialize(graph.engineContext)
            planningInitialized = true
            graph.executionEngine.initialize(graph.engineContext)
            executionInitialized = true

            checkHostStillConnected()

            graph.planningEngine.start()
            planningStarted = true
            graph.executionEngine.start()
            executionStarted = true

            /*
             * Audio perception
             */
            graph.audioPerceptionEngine.initialize(
                graph.engineContext
            )
            audioInitialized = true

            checkHostStillConnected()

            graph.audioPerceptionEngine.start()
            audioStarted = true

            /*
             * Kernel
             */
            graph.microkernel.boot()
            kernelBooted = true

            started = true
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                cleanupPartialStartup(
                    audioStarted = audioStarted,
                    audioInitialized = audioInitialized,
                    executionStarted = executionStarted,
                    executionInitialized = executionInitialized,
                    planningStarted = planningStarted,
                    planningInitialized = planningInitialized,
                    reasoningStarted = reasoningStarted,
                    reasoningInitialized = reasoningInitialized,
                    interpretationStarted = interpretationStarted,
                    interpretationInitialized = interpretationInitialized,
                    kernelBooted = kernelBooted
                )
            }
            throw error
        }
    }

    suspend fun stop() = lifecycleMutex.withLock {
        if (!started) {
            return@withLock
        }

        withContext(NonCancellable) {
            cleanupPartialStartup(
                audioStarted = true,
                audioInitialized = true,
                executionStarted = true,
                executionInitialized = true,
                planningStarted = true,
                planningInitialized = true,
                reasoningStarted = true,
                reasoningInitialized = true,
                interpretationStarted = true,
                interpretationInitialized = true,
                kernelBooted = true
            )

            /*
             * Disconnect physical host last.
             */
            graph.wearableHost.disconnect()
            started = false
        }
    }

    private fun checkHostStillConnected() {
        val connectivity = graph.wearableHost.connectivity.value
        check(
            connectivity is ConnectionState.Connected || connectivity is ConnectionState.Ready
        ) {
            "Wearable host disconnected during runtime startup"
        }
    }

    private suspend fun cleanupPartialStartup(
        audioStarted: Boolean,
        audioInitialized: Boolean,
        executionStarted: Boolean,
        executionInitialized: Boolean,
        planningStarted: Boolean,
        planningInitialized: Boolean,
        reasoningStarted: Boolean,
        reasoningInitialized: Boolean,
        interpretationStarted: Boolean,
        interpretationInitialized: Boolean,
        kernelBooted: Boolean
    ) {
        /*
         * Reverse startup order. Each operation is guarded independently so one
         * broken component cannot prevent the remaining runtime from cleaning up.
         */
        if (kernelBooted) {
            runCatching { graph.microkernel.shutdown() }
        }

        if (audioStarted) {
            runCatching { graph.audioPerceptionEngine.stop() }
        }
        if (audioInitialized) {
            runCatching { graph.audioPerceptionEngine.dispose() }
        }

        if (executionStarted) {
            runCatching { graph.executionEngine.stop() }
        }
        if (executionInitialized) {
            runCatching { graph.executionEngine.dispose() }
        }

        if (planningStarted) {
            runCatching { graph.planningEngine.stop() }
        }
        if (planningInitialized) {
            runCatching { graph.planningEngine.dispose() }
        }

        if (reasoningStarted) {
            runCatching { graph.reasoningEngine.stop() }
        }
        if (reasoningInitialized) {
            runCatching { graph.reasoningEngine.dispose() }
        }

        if (interpretationStarted) {
            runCatching { graph.interpretationEngine.stop() }
        }
        if (interpretationInitialized) {
            runCatching { graph.interpretationEngine.dispose() }
        }
    }
}
