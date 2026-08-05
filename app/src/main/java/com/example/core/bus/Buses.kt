package com.example.core.bus

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

interface Event
interface Command
interface Query

class EventBus {
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    val events: SharedFlow<Event> = _events
    
    suspend fun publish(event: Event) {
        _events.emit(event)
    }
}

class CommandBus {
    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 64)
    val commands: SharedFlow<Command> = _commands
    
    suspend fun send(command: Command): Boolean {
        _commands.emit(command)
        return true
    }
}

class QueryBus {
    private val _queries = MutableSharedFlow<Query>(extraBufferCapacity = 64)
    val queries: SharedFlow<Query> = _queries
    
    suspend fun send(query: Query): Any? {
        _queries.emit(query)
        return null // Simplification for now
    }
}
