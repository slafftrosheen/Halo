package com.halo.runtime.observer

import com.halo.sdk.runtime.EngineHealth
import com.halo.sdk.runtime.EngineSnapshot
import com.halo.sdk.runtime.EngineState
import com.halo.sdk.runtime.RuntimeHealth
import com.halo.sdk.runtime.SpeechSessionState
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
        val hasFailure = engines.any { engine ->
            engine.state is EngineState.Failed || engine.health == EngineHealth.Failed
        }
        val degraded = hasFailure || engines.any { engine ->
            engine.health == EngineHealth.Degraded || engine.health == EngineHealth.Recovering
        }
        val allRunning = engines.isNotEmpty() && engines.all { engine ->
            engine.state == EngineState.Running &&
                engine.health != EngineHealth.Failed &&
                engine.health != EngineHealth.Offline
        }

        return RuntimeHealth(
            healthy = !isBooting && allRunning && !degraded,
            uptime = Duration.between(startedAtTime, clock.instant()),
            degraded = degraded,
            lastFailure = if (hasFailure) clock.instant() else null
        )
    }
}
