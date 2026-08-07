package com.halo.sdk.runtime

interface RuntimeContext {
    val eventBus: EventBus
    val commandBus: CommandBus
    val queryBus: QueryBus
    val scheduler: RuntimeScheduler
    val registry: ServiceRegistry
    val logger: RuntimeLogger
    // val clock: Clock (skipping for now or define a simple one)
}
