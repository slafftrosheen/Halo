package com.halo.libraries.testing

import com.halo.sdk.runtime.*

class FakeRuntimeContext(
    override val eventBus: EventBus,
    override val commandBus: CommandBus,
    override val queryBus: QueryBus,
    override val scheduler: Scheduler,
    override val registry: ServiceRegistry,
    override val logger: HaloLogger
) : RuntimeContext
