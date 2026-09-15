package com.halo.app

import com.halo.app.di.ManualDependencyGraph
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the application-level runtime lifecycle.
 *
 * Construction != startup. Halo's cognitive runtime is usable without a
 * wearable; physical-device capabilities become available when a host connects.
 */
class HaloRuntime(
    private val graph: ManualDependencyGraph
) {
    private val lifecycleMutex = Mutex()
    private var started = false

    fun isStarted() = started

    suspend fun start() = lifecycleMutex.withLock {
        check(!started) { "HaloRuntime is already running" }

        RuntimeStartupTrace.mark("runtime.start.begin")

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
        var speechSessionStarted = false
        var speechBridgeStarted = false
        var replayRecordingStarted = false
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
            phase("interpretation.initialize") {
                graph.interpretationEngine.initialize(graph.engineContext)
                interpretationInitialized = true
            }
            phase("reasoning.initialize") {
                graph.reasoningEngine.initialize(graph.engineContext)
                reasoningInitialized = true
            }

            phase("interpretation.start") {
                graph.interpretationEngine.start()
                interpretationStarted = true
            }
            phase("reasoning.start") {
                graph.reasoningEngine.start()
                reasoningStarted = true
            }

            phase("planning.initialize") {
                graph.planningEngine.initialize(graph.engineContext)
                planningInitialized = true
            }
            phase("execution.initialize") {
                graph.executionEngine.initialize(graph.engineContext)
                executionInitialized = true
            }

            phase("planning.start") {
                graph.planningEngine.start()
                planningStarted = true
            }
            phase("execution.start") {
                graph.executionEngine.start()
                executionStarted = true
            }

            phase("audio.initialize") {
                graph.audioPerceptionEngine.initialize(graph.engineContext)
                audioInitialized = true
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
            phase("replay.start") {
                graph.runtimeReplayRecorder.startRecording()
                replayRecordingStarted = true
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
                cleanupPartialStartup(
                    audioStarted, audioInitialized,
                    executionStarted, executionInitialized,
                    planningStarted, planningInitialized,
                    reasoningStarted, reasoningInitialized,
                    interpretationStarted, interpretationInitialized,
                    speechSessionStarted, speechBridgeStarted,
                    replayRecordingStarted, androidSpeakerStarted, kernelBooted
                )
            }
            throw error
        }
    }

    suspend fun stop() = lifecycleMutex.withLock {
        if (!started) return@withLock
        RuntimeStartupTrace.mark("runtime.stop.begin")
        withContext(NonCancellable) {
            cleanupPartialStartup(
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true
            )
            graph.wearableHost.disconnect()
            started = false
            RuntimeStartupTrace.mark("runtime.stop.complete")
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
        speechSessionStarted: Boolean,
        speechBridgeStarted: Boolean,
        replayRecordingStarted: Boolean,
        androidSpeakerStarted: Boolean,
        kernelBooted: Boolean
    ) {
        if (kernelBooted) runCatching { graph.microkernel.shutdown() }
        if (androidSpeakerStarted) runCatching { graph.androidSpeaker.shutdown() }
        if (replayRecordingStarted) runCatching { graph.runtimeReplayRecorder.stopRecording() }
        if (speechBridgeStarted) runCatching { graph.speechPerceptionBridge.stop() }
        if (speechSessionStarted) runCatching { graph.speechSessionManager.stop() }
        if (audioStarted) runCatching { graph.audioPerceptionEngine.stop() }
        if (audioInitialized) runCatching { graph.audioPerceptionEngine.dispose() }
        if (executionStarted) runCatching { graph.executionEngine.stop() }
        if (executionInitialized) runCatching { graph.executionEngine.dispose() }
        if (planningStarted) runCatching { graph.planningEngine.stop() }
        if (planningInitialized) runCatching { graph.planningEngine.dispose() }
        if (reasoningStarted) runCatching { graph.reasoningEngine.stop() }
        if (reasoningInitialized) runCatching { graph.reasoningEngine.dispose() }
        if (interpretationStarted) runCatching { graph.interpretationEngine.stop() }
        if (interpretationInitialized) runCatching { graph.interpretationEngine.dispose() }
    }
}
