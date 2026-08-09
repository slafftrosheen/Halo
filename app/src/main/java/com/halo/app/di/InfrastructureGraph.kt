package com.halo.app.di

import com.halo.libraries.logging.ConsoleLogger
import com.halo.runtime.commands.DefaultCommandBus
import com.halo.runtime.events.DefaultEventBus
import com.halo.runtime.queries.DefaultQueryBus
import com.halo.runtime.registry.DefaultServiceRegistry
import com.halo.runtime.scheduler.DefaultScheduler
import com.halo.app.devtools.RecordingEventBus
import com.halo.app.devtools.CognitiveTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.halo.sdk.runtime.InMemoryRuntimeMetrics
import com.halo.sdk.runtime.RuntimeClock
import com.halo.sdk.runtime.RuntimePowerMode
import com.halo.sdk.runtime.RuntimeMetricsSink
import com.halo.sdk.runtime.SystemRuntimeClock

/**
 * Owns all cross-cutting infrastructure: buses, logger, scheduler, scope.
 * No domain logic. No Android dependencies.
 */
class InfrastructureGraph {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: RuntimeClock = SystemRuntimeClock()
    val metrics: RuntimeMetricsSink = InMemoryRuntimeMetrics()
    var powerMode: RuntimePowerMode = RuntimePowerMode.BALANCED

    val eventBus by lazy { RecordingEventBus(DefaultEventBus()) }
    val commandBus by lazy { DefaultCommandBus() }
    val queryBus by lazy { DefaultQueryBus() }
    val scheduler by lazy { DefaultScheduler() }
    val registry by lazy {
        DefaultServiceRegistry().apply {
            register(com.halo.runtime.services.Services.EventBus, eventBus)
        }
    }
    val logger by lazy { ConsoleLogger() }

    val perceptionBus by lazy { com.halo.runtime.kernel.bus.DefaultPerceptionBus() }
    val cognitiveBus by lazy { com.halo.runtime.kernel.bus.DefaultCognitiveBus() }
    val executionBus by lazy { com.halo.runtime.kernel.bus.DefaultExecutionBus() }
    val blackboard by lazy { com.halo.runtime.cognition.blackboard.DefaultBlackboard() }

    val cognitiveTrace by lazy {
        CognitiveTrace(scope, perceptionBus, cognitiveBus, executionBus).apply { start() }
    }
}
