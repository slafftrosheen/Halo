package com.halo.sdk.runtime

import com.halo.libraries.common.core.Identifier
import kotlinx.coroutines.flow.Flow

interface Event {
    val id: Identifier
    val timestamp: Long
}

interface EventBus {
    suspend fun publish(event: Event)
    fun subscribe(): Flow<Event>
}
