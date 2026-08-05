package com.halo.engines.reasoning.service

import com.halo.runtime.cognition.Thought
import java.util.UUID

class ThoughtRepository {
    private val thoughts = mutableMapOf<UUID, Thought>()

    fun save(thought: Thought) {
        thoughts[thought.id] = thought
    }

    fun getRecentThoughts(): List<Thought> {
        return thoughts.values.sortedByDescending { it.timestamp }.take(10)
    }
}
