package com.halo.engines.interpretation

import com.halo.sdk.runtime.Observation

interface ObservationInterpreter {
    suspend fun interpret(observation: Observation, context: InterpretationContext): Interpretation?
}
