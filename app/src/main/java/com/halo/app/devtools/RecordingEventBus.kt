package com.halo.app.devtools

import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class RecordingEventBus(
    private val delegate: EventBus
) : EventBus {
    private val _history = MutableStateFlow<List<EventEnvelope<*>>>(emptyList())
    val history: Flow<List<EventEnvelope<*>>> = _history.asStateFlow()

    override suspend fun publish(event: HaloEvent) {
        val envelope = EventEnvelope(
            id = EventId(UUID.randomUUID().toString()),
            event = event,
            createdAt = System.currentTimeMillis(),
            sourceEngine = EngineId("unknown"),
            intentId = null,
            parentId = null,
            traceId = null,
            spanId = null,
            parentSpanId = null
        )
        _history.update { current ->
            (current + envelope).takeLast(500)
        }
        delegate.publish(event)
    }

    override fun events(): Flow<HaloEvent> = delegate.events()

    override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = delegate.observe(type)
}
