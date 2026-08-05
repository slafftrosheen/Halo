package com.halo.sdk.runtime

interface RuntimeContext {
    val eventBus: EventBus
    val commandBus: CommandBus
    val queryBus: QueryBus
    val scheduler: Scheduler
    val registry: ServiceRegistry
    val logger: HaloLogger
    // val clock: Clock (skipping for now or define a simple one)
}
