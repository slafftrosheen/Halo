package com.halo.engines.reasoning.service

import com.halo.runtime.cognition.AttentionState
import kotlinx.datetime.Clock

class AttentionManager {
    private var currentState: AttentionState = AttentionState(
        currentFocus = null,
        recentThoughts = emptyList(),
        currentTask = null,
        failures = emptyList(),
        lastUpdated = Clock.System.now()
    )

    fun updateAttention(newState: AttentionState) {
        this.currentState = newState
    }

    fun getAttention(): AttentionState = currentState
}
