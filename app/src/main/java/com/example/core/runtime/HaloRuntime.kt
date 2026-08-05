package com.example.core.runtime

import com.example.core.engine.Engine
import com.example.core.bus.EventBus
import com.example.core.bus.CommandBus
import com.example.core.bus.QueryBus
import com.example.core.configuration.HaloConfiguration

class HaloRuntime(
    val eventBus: EventBus,
    val commandBus: CommandBus,
    val queryBus: QueryBus
) {
    private val engines = mutableListOf<Engine>()
    private var isReady = false

    fun registerEngine(engine: Engine) {
        engines.add(engine)
    }

    suspend fun boot(config: HaloConfiguration) {
        // Pseudo Boot Sequence
        // 1. Verify config
        // 2. Init logging
        // 3. Init DB
        // 4. Start Buses (already instantiated)
        // 5. Load engines
        for (engine in engines) {
            engine.initialize()
        }
        for (engine in engines) {
            engine.start()
        }
        // 6. Detect devices
        // 7. Load providers
        // 8. Load plugins
        // 9. Restore jobs
        isReady = true
    }

    suspend fun shutdown() {
        isReady = false
        // Reverse order shutdown
        for (engine in engines.reversed()) {
            engine.stop()
            engine.destroy()
        }
    }
}
