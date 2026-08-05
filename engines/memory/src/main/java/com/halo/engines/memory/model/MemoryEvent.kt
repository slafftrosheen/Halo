package com.halo.runtime.cognition.memory

sealed interface MemoryEvent {
    val memory: Memory
}

data class MemoryStored(override val memory: Memory) : MemoryEvent
data class MemoryUpdated(override val memory: Memory) : MemoryEvent
data class MemoryForgotten(override val memory: Memory) : MemoryEvent
data class MemoryRetrieved(override val memory: Memory) : MemoryEvent

