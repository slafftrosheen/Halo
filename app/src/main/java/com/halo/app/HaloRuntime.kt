package com.halo.app

import com.halo.app.di.ManualDependencyGraph
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the process-level Halo runtime lifecycle.
 *
 * Construction != startup. The same instance survives Activity recreation so
 * configuration changes cannot dispose and race a newly-created UI against the
 * old Activity's teardown. Physical-device capabilities remain optional.
 */
class HaloRuntime(
    private val graph: ManualDependencyGraph
) {
    private val lifecycleMutex = Mutex()

    @Volatile
    private var started = false

    /** Engines are initialized once for the lifetime of the process graph. */
    private var initialized = false

    fun isStarted(): Boolean = started

    /** Idempotent so overlapping Activity bootstrap attempts safely converge. */
    suspend fun start() = lifecycleMutex.withLock {
        if (started) return@withLock

        RuntimeStartupTrace.mark("runtime.start.begin")

        var interpretationStarted = false
        var reasoningStarted = false
        var planningStarted = false
        var executionStarted = false
        var commandResponseStarted = false
        var audioStarted = false
        var speechSessionStarted = false
        var speechBridgeStarted = false
        var androidSpeakerStarted = false
        var kernelBooted = false

        suspend fun phase(name: String, block: suspend () -> Unit) {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            RuntimeStartupTrace.mark("runtime.$name.begin")
            try {
                block()
            } finally {
                RuntimeStartupTrace.mark("runtime.$name.end", startedAt)
            }
        }

        try {
            if (!initialized) {
                // Initialize the complete phone runtime before starting any
                // reactive collectors. This gives retry a clean phase boundary.
                phase("interpretation.initialize") {
                    graph.interpretationEngine.initialize(graph.engineContext)
                }
                phase("reasoning.initialize") {
                    graph.reasoningEngine.initialize(graph.engineContext)
                }
                phase("planning.initialize") {
                    graph.planningEngine.initialize(graph.engineContext)
                }
                phase("execution.initialize") {
                    graph.executionEngine.initialize(graph.engineContext)
                }
                phase("audio.initialize") {
                    graph.audioPerceptionEngine.initialize(graph.engineContext)
                }
                initialized = true
                RuntimeStartupTrace.mark("runtime.initialize.complete")
            }

            phase("interpretation.start") {
                graph.interpretationEngine.start()
                interpretationStarted = true
            }
            phase("reasoning.start") {
                graph.reasoningEngine.start()
                reasoningStarted = true
            }
            phase("planning.start") {
                graph.planningEngine.start()
                planningStarted = true
            }
            phase("execution.start") {
                graph.executionEngine.start()
                executionStarted = true
            }
            phase("commandResponse.start") {
                graph.commandResponseProcess.start()
                commandResponseStarted = true
            }
            phase("audio.start") {
                graph.audioPerceptionEngine.start()
                audioStarted = true
            }
            phase("speechSession.start") {
                graph.speechSessionManager.start()
                speechSessionStarted = true
            }
            phase("speechBridge.start") {
                graph.speechPerceptionBridge.start()
                speechBridgeStarted = true
            }
            phase("androidSpeaker.start") {
                graph.startAndroidSpeaker()
                androidSpeakerStarted = true
            }
            phase("microkernel.boot") {
                graph.microkernel.boot()
                kernelBooted = true
            }

            started = true
            RuntimeStartupTrace.mark("runtime.start.complete")
        } catch (error: Throwable) {
            RuntimeStartupTrace.mark("runtime.start.failed")
            withContext(NonCancellable) {
                stopStartedComponents(
                    interpretationStarted = interpretationStarted,
                    reasoningStarted = reasoningStarted,
                    planningStarted = planningStarted,
                    executionStarted = executionStarted,
                    commandResponseStarted = commandResponseStarted,
                    audioStarted = audioStarted,
                    speechSessionStarted = speechSessionStarted,
                    speechBridgeStarted = speechBridgeStarted,
                    androidSpeakerStarted = androidSpeakerStarted,
                    kernelBooted = kernelBooted
                )
            }
            started = false
            throw error
        }
    }

    /**
     * Stops active collectors/resources but deliberately does not dispose the
     * process graph. A later start() must remain valid in the same Android process.
     */
    suspend fun stop() = lifecycleMutex.withLock {
        if (!started) return@withLock

        RuntimeStartupTrace.mark("runtime.stop.begin")
        withContext(NonCancellable) {
            stopStartedComponents(
                interpretationStarted = true,
                reasoningStarted = true,
                planningStarted = true,
                executionStarted = true,
                commandResponseStarted = true,
                audioStarted = true,
                speechSessionStarted = true,
                speechBridgeStarted = true,
                androidSpeakerStarted = true,
                kernelBooted = true
            )
            started = false
            RuntimeStartupTrace.mark("runtime.stop.complete")
        }
    }

    private suspend fun stopStartedComponents(
        interpretationStarted: Boolean,
        reasoningStarted: Boolean,
        planningStarted: Boolean,
        executionStarted: Boolean,
        commandResponseStarted: Boolean,
        audioStarted: Boolean,
        speechSessionStarted: Boolean,
        speechBridgeStarted: Boolean,
        androidSpeakerStarted: Boolean,
        kernelBooted: Boolean
    ) {
        if (kernelBooted) runCatching { graph.microkernel.shutdown() }
        if (androidSpeakerStarted) runCatching { graph.androidSpeaker.shutdown() }
        if (speechBridgeStarted) runCatching { graph.speechPerceptionBridge.stop() }
        if (speechSessionStarted) runCatching { graph.speechSessionManager.stop() }
        if (audioStarted) runCatching { graph.audioPerceptionEngine.stop() }
        if (commandResponseStarted) runCatching { graph.commandResponseProcess.stop() }
        if (executionStarted) runCatching { graph.executionEngine.stop() }
        if (planningStarted) runCatching { graph.planningEngine.stop() }
        if (reasoningStarted) runCatching { graph.reasoningEngine.stop() }
        if (interpretationStarted) runCatching { graph.interpretationEngine.stop() }
    }
}
