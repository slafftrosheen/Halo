package com.halo.app.di

import android.content.Context
import com.halo.runtime.observer.DefaultRuntimeObserver
import com.halo.runtime.observer.TimelineRecorder
import com.halo.runtime.observer.DefaultTimelineEventMapper
import com.halo.runtime.observer.DefaultRuntimeHealthEvaluator
import com.halo.runtime.observer.DefaultSnapshotAssembler
import com.halo.sdk.runtime.ProviderSnapshot

/**
 * Owns all observability infrastructure: timeline, replay, snapshots, tracing.
 * Read-only consumers of runtime state — never mutate engine behavior.
 */
class ObserverGraph(
    private val applicationContext: Context,
    private val infra: InfrastructureGraph,
    private val engineGraph: EngineGraph,
    private val providerGraph: ProviderGraph
) {

    val timelineEventMapper by lazy { DefaultTimelineEventMapper() }
    val timelineRecorder by lazy {
        TimelineRecorder(infra.eventBus, timelineEventMapper, infra.scope)
    }

    val runtimeValidator by lazy { com.halo.runtime.observer.RuntimeValidator() }
    val traceRecorder by lazy {
        com.halo.runtime.observer.TraceRecorder(infra.eventBus, runtimeValidator, infra.scope)
    }

    val healthEvaluator by lazy {
        DefaultRuntimeHealthEvaluator(java.time.Clock.systemUTC())
    }

    val snapshotAssembler by lazy {
        DefaultSnapshotAssembler(healthEvaluator, java.time.Clock.systemUTC())
    }

    val runtimeReplayRecorder by lazy {
        com.halo.runtime.observer.replay.RuntimeReplayRecorder(
            infra.eventBus,
            infra.scope,
            java.io.File(applicationContext.cacheDir, "replay_sessions")
        )
    }

    val runtimeReplayPlayer by lazy {
        com.halo.runtime.observer.replay.RuntimeReplayPlayer(
            infra.eventBus,
            infra.scope
        )
    }

    val runtimeSnapshotManager by lazy {
        DefaultRuntimeObserver(
            engineGraph.speechSessionManager,
            processManager,
            infra.blackboard,
            timelineRecorder,
            traceRecorder,
            snapshotAssembler,
            {
                val inference = providerGraph.providers.inference
                val aiProviders = providerGraph.providers.aiProviders
                
                listOf(
                    ProviderSnapshot(inference.metadata.id, inference.metadata.displayName, "Healthy")
                ) + aiProviders.map { 
                    ProviderSnapshot(it.id, it.name, "Active")
                }
            },
            java.time.Clock.systemUTC(),
            infra.scope
        )
    }

    /** ProcessManager is created here — it owns process lifecycle */
    val processManager by lazy {
        com.halo.runtime.kernel.ProcessManager(infra.scope, infra.logger)
    }
}
