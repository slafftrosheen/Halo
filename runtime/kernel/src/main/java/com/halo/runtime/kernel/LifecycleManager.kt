package com.halo.runtime.kernel

import com.halo.sdk.runtime.*

class LifecycleManager {
    
    private suspend fun <T> withRecovery(
        engine: Engine,
        context: RuntimeContext,
        action: suspend () -> T
    ): T? {
        var attempts = 0
        while (attempts < 4) {
            try {
                return action()
            } catch (e: Exception) {
                attempts++
                if (attempts > 3) {
                    context.eventBus.publish(EngineFailed(engine.metadata.id.value, e.message ?: "Failed after 3 retries"))
                    return null
                }
                
                if (engine is RecoverableEngine) {
                    try {
                        engine.recover()
                    } catch (recoverError: Exception) {
                        // ignore recover error and just retry
                    }
                }
            }
        }
        return null
    }

    suspend fun initialize(engines: List<EngineNode>, context: RuntimeContext) {
        engines.forEach { node ->
            context.eventBus.publish(EngineInitializing(node.engine.metadata.id.value))
            val result = withRecovery(node.engine, context) {
                node.engine.initialize(EngineContext(context.registry, context))
                true
            }
            if (result == null) {
                throw IllegalStateException("Engine failed to initialize: ${node.engine.metadata.id.value}")
            }
        }
    }

    suspend fun start(engines: List<EngineNode>, context: RuntimeContext) {
        engines.forEach { node ->
            val result = withRecovery(node.engine, context) {
                node.engine.start()
                true
            }
            if (result != null) {
                context.eventBus.publish(EngineStarted(node.engine.metadata.id.value))
            } else {
                throw IllegalStateException("Engine failed to start: ${node.engine.metadata.id.value}")
            }
        }
    }

    suspend fun stop(engines: List<EngineNode>, context: RuntimeContext) {
        engines.asReversed().forEach { node ->
            try {
                node.engine.stop()
                context.eventBus.publish(EngineStopped(node.engine.metadata.id.value))
            } catch (e: Exception) {
                context.eventBus.publish(EngineFailed(node.engine.metadata.id.value, e.message ?: "Unknown error during stop"))
            }
        }
    }
}
