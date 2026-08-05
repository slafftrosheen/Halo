package com.halo.engines.kernel.manager

import com.halo.icl.core.CognitiveObject

class SecurityManager {
    fun canPublish(processId: String, busName: String, obj: CognitiveObject): Boolean {
        // Enforce sandbox. e.g., only "system.planner" can publish "Plan" to ExecutionBus
        return true // Default open for now
    }

    fun canSubscribe(processId: String, busName: String): Boolean {
        return true
    }
}
