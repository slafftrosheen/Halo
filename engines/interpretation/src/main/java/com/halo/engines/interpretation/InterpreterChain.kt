package com.halo.engines.interpretation

import com.halo.sdk.runtime.Observation

class InterpreterChain(
    private val interpreters: List<ObservationInterpreter>
) {
    suspend fun interpret(observation: Observation, context: InterpretationContext): Interpretation? {
        for (interpreter in interpreters) {
            val interpretation = interpreter.interpret(observation, context)
            if (interpretation != null) {
                return interpretation
            }
        }
        return null
    }
}
