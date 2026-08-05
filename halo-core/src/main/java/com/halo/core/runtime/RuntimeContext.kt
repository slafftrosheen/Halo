package com.halo.core.runtime

/**
 * The single dependency provided to every Engine, Provider, and Plugin.
 * Instead of passing individual buses and registries, they receive the Context.
 */
interface RuntimeContext {
    val eventBus: Any // Will map to EventBus interface
    val commandBus: Any // Will map to CommandBus interface
    val queryBus: Any // Will map to QueryBus interface
    val services: Any // Will map to ServiceRegistry
    val scheduler: Any // Will map to Task Scheduler
    val logger: Any // Will map to Observatory/Logger
    val clock: Any // Will map to System Clock
    val configuration: Any // Will map to Platform Config
}
