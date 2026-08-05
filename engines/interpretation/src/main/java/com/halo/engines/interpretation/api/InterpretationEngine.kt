package com.halo.engines.interpretation.api

import com.halo.sdk.runtime.Engine
import com.halo.sdk.runtime.Observation

interface InterpretationEngine : Engine {
    suspend fun interpret(
        observation: Observation
    ): InterpretationResult
}
