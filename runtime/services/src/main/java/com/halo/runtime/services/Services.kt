package com.halo.runtime.services

import com.halo.sdk.runtime.ServiceKey
import com.halo.sdk.provider.AiInferenceProvider

object Services {
    val EventBus = ServiceKey(com.halo.sdk.runtime.EventBus::class)
    val Blackboard = ServiceKey(com.halo.runtime.cognition.blackboard.Blackboard::class)
    val CommandBus = ServiceKey(com.halo.sdk.runtime.CommandBus::class)
    val Driver = ServiceKey(com.halo.sdk.driver.Driver::class)

    /**
     * Runtime inference service boundary.
     *
     * The key name remains stable for callers while the service type uses the
     * typed inference contract. External assistant handoff is intentionally
     * not exposed through this service key.
     */
    val InferenceProvider = ServiceKey(AiInferenceProvider::class)

    val PluginManager = ServiceKey(com.halo.runtime.plugins.PluginManager::class)
}
