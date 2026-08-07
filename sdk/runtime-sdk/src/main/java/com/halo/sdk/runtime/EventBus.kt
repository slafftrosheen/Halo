package com.halo.sdk.runtime

import kotlinx.coroutines.flow.Flow

interface HaloEvent {
    val id: String
    val timestamp: Long
}

interface EventBus {
    suspend fun publish(event: HaloEvent)
    fun events(): Flow<HaloEvent>
    fun <T : HaloEvent> observe(type: Class<T>): Flow<T>
}
