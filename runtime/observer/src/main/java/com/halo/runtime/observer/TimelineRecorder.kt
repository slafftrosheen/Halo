package com.halo.runtime.observer

import com.halo.sdk.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.ArrayDeque

class TimelineRecorder(
    private val eventBus: EventBus,
    private val mapper: TimelineEventMapper,
    private val scope: CoroutineScope,
    private val additionalEvents: Flow<List<TimelineEvent>> = flowOf(emptyList()),
) {
    companion object {
        const val MAX_TIMELINE_EVENTS = 100
    }

    private val _timelineFlow = MutableStateFlow<List<TimelineEvent>>(emptyList())
    val timelineFlow: StateFlow<List<TimelineEvent>> = _timelineFlow.asStateFlow()

    init {
        val deque = ArrayDeque<TimelineEvent>(MAX_TIMELINE_EVENTS)
        val recorded=MutableStateFlow<List<TimelineEvent>>(emptyList())
        scope.launch {
            combine(recorded,additionalEvents) {runtime,additional ->
                (runtime+additional).distinctBy {it.presentationId}.sortedBy {it.timestamp}.takeLast(MAX_TIMELINE_EVENTS)
            }.collect {_timelineFlow.value=it}
        }
        scope.launch {
            eventBus.events().collect { event ->
                val newEvent = mapper.map(event)
                if (newEvent != null) {
                    if (deque.size >= MAX_TIMELINE_EVENTS) {
                        deque.removeFirst()
                    }
                    deque.addLast(newEvent)
                    recorded.value = deque.toList()
                }
            }
        }
    }
}


