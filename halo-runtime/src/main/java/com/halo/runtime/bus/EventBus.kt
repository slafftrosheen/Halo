package com.halo.runtime.bus

import kotlinx.coroutines.flow.SharedFlow

/**
 * The EventBus is the central nervous system of the HAL Runtime Kernel.
 * All state changes, observations, and system events pass through here.
 */
interface EventBus {
    val events: SharedFlow<HaloEvent>
    
    suspend fun publish(event: HaloEvent)
    
    // Allows subscribing to specific event types
    fun <T : HaloEvent> subscribe(eventType: Class<T>): SharedFlow<T>
}

interface HaloEvent {
    val timestamp: Long
    val source: String
}
