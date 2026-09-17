package com.halo.app.di

import android.content.Context
import com.halo.runtime.observer.DefaultRuntimeObserver
import com.halo.runtime.observer.TimelineRecorder
import com.halo.runtime.observer.DefaultTimelineEventMapper
import com.halo.runtime.observer.DefaultRuntimeHealthEvaluator
import com.halo.runtime.observer.DefaultSnapshotAssembler
import com.halo.sdk.runtime.ProviderSnapshot

/**
 * Owns production observability infrastructure used to expose runtime state.
 * Diagnostics are emitted to Logcat; replay recording is deliberately not part
 * of the production dependency graph.
 */
class ObserverGraph(
    private val applicationContext: Context,
    private val infra: InfrastructureGraph,
    private val engineGraph: EngineGraph
) {

    val timelineEventMapper by lazy { DefaultTimelineEventMapper() }
    val timelineRecorder by lazy {
        TimelineRecorder(infra.eventBus, timelineEventMapper, infra.scope)
    }

    val runtimeValidator by lazy { com.halo.runtime.observer.RuntimeValidator() }
    val traceRecorder by lazy {
        com.halo.runtime.observer.TraceRecorder(infra.eventBus, runtimeValidator, infra.scope)
    }

    val speechTurnTracker by lazy {
        com.halo.runtime.observer.SpeechTurnTracker(infra.eventBus, infra.scope)
    }

    val healthEvaluator by lazy {
        DefaultRuntimeHealthEvaluator(java.time.Clock.systemUTC())
    }

    val snapshotAssembler by lazy {
        DefaultSnapshotAssembler(
            healthEvaluator,
            java.time.Clock.systemUTC(),
            engineGraph.nativeResourceCoordinator
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
                // Defer evaluating providers until the snapshot flow actually runs
                // to avoid resolving the lazy aiProviderRouter on the UI thread
                // during early ObserverGraph construction.
                val router = engineGraph.aiProviderRouter
                val selectedId = router.selectedProviderId.value
                val selectedModelId = router.selectedModelId.value

                router.getAllProviders().map { provider ->
                    val status = provider.status.value
                    ProviderSnapshot(
                        id = provider.id,
                        name = provider.displayName,
                        status = status.state.name,
                        statusMessage = status.message,
                        isSelected = provider.id == selectedId,
                        activeModel = if (provider.id == selectedId) selectedModelId else null
                    )
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
