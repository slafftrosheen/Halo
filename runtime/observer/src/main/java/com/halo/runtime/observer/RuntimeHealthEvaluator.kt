package com.halo.runtime.observer

import com.halo.sdk.runtime.*
import java.time.Clock
import java.time.Duration
import java.time.Instant

interface RuntimeHealthEvaluator {
    fun evaluate(
        isBooting: Boolean,
        startedAtTime: Instant,
        speechState: SpeechSessionState,
        engines: List<EngineSnapshot>
    ): RuntimeHealth
}

class DefaultRuntimeHealthEvaluator(
    private val clock: Clock
) : RuntimeHealthEvaluator {
    override fun evaluate(
        isBooting: Boolean,
        startedAtTime: Instant,
        speechState: SpeechSessionState,
        engines: List<EngineSnapshot>
    ): RuntimeHealth {
        val allRequiredEnginesHealthy = engines.any { it.id == "Planning" && it.health == EngineHealth.Healthy }
        
        // For now, if booting we're not healthy, else if missing engines we rely on speech & planning logic eventually
        val isHealthy = !isBooting

        return RuntimeHealth(
            healthy = isHealthy,
            uptime = Duration.between(startedAtTime, clock.instant()),
            degraded = false,
            lastFailure = null
        )
    }
}
