package com.halo.runtime.events

import com.halo.sdk.runtime.Event
import com.halo.sdk.runtime.EventBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DefaultEventBus : EventBus {
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)

    override suspend fun publish(event: Event) {
        _events.emit(event)
    }

    override fun subscribe(): Flow<Event> = _events.asSharedFlow()
}
