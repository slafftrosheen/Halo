package com.halo.app.di

import com.halo.app.pipeline.VoicePipeline
import com.halo.libraries.logging.DefaultHaloLogger
import com.halo.libraries.testing.FakeDriver
import com.halo.libraries.testing.FakeProvider
import com.halo.libraries.testing.FakeRuntimeContext
import com.halo.runtime.commands.DefaultCommandBus
import com.halo.runtime.events.DefaultEventBus
import com.halo.runtime.kernel.HaloRuntime
import com.halo.runtime.queries.DefaultQueryBus
import com.halo.runtime.registry.DefaultServiceRegistry
import com.halo.runtime.scheduler.DefaultScheduler
import com.halo.sdk.runtime.*

object ManualDependencyGraph {
    
    val eventBus by lazy { DefaultEventBus() }
    val commandBus by lazy { DefaultCommandBus() }
    val queryBus by lazy { DefaultQueryBus() }
    val scheduler by lazy { DefaultScheduler() }
    val registry by lazy { DefaultServiceRegistry() }
    val logger by lazy { DefaultHaloLogger() }
    
    val context by lazy {
        FakeRuntimeContext(
            eventBus = eventBus,
            commandBus = commandBus,
            queryBus = queryBus,
            scheduler = scheduler,
            registry = registry,
            logger = logger
        )
    }

    val driver by lazy { FakeDriver("heycyan.mock") }
    val provider by lazy { FakeProvider("llamacpp.mock") }
    
    val voicePipeline by lazy { VoicePipeline(driver, provider) }
    
    val runtime by lazy {
        HaloRuntime(
            engines = listOf(voicePipeline),
            runtimeContext = context
        )
    }
}
