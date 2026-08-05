package com.halo.runtime.kernel.bus

import com.halo.icl.core.CognitiveObject

// Allows Plans, Tasks, Outcomes
interface ExecutionBus : SystemBus<CognitiveObject> {
    override val name: String
        get() = "ExecutionBus"
}
