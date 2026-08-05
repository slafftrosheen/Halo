package com.halo.runtime.kernel.bus

import com.halo.icl.core.CognitiveObject

// Allows Thoughts, Beliefs, Goals, Intentions, Hypotheses, Predictions
interface CognitiveBus : SystemBus<CognitiveObject> {
    override val name: String
        get() = "CognitiveBus"
}
