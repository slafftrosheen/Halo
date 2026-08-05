package com.halo.runtime.kernel

import com.halo.sdk.runtime.*

class BootLoader(
    private val graph: EngineGraph,
    private val resolver: DependencyResolver,
    private val lifecycle: LifecycleManager
) {
    suspend fun boot(context: RuntimeContext, updateState: (RuntimeState) -> Unit) {
        updateState(RuntimeState.Booting)
        context.eventBus.publish(RuntimeBooting())

        val sorted = resolver.resolve(graph)
        lifecycle.initialize(sorted, context)
        lifecycle.start(sorted, context)

        context.eventBus.publish(RuntimeStarted())
        updateState(RuntimeState.Running)
    }

    suspend fun shutdown(context: RuntimeContext, updateState: (RuntimeState) -> Unit) {
        updateState(RuntimeState.ShuttingDown)
        context.eventBus.publish(RuntimeStopping())

        val sorted = resolver.resolve(graph)
        lifecycle.stop(sorted, context)

        sorted.asReversed().forEach {
            it.engine.dispose()
        }

        context.eventBus.publish(RuntimeStopped())
        updateState(RuntimeState.Offline)
    }
}
