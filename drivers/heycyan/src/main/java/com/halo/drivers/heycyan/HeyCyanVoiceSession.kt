package com.halo.drivers.heycyan

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * State machine for HeyCyan physical voice activation sessions.
 *
 * Sequence verified from decompiled SDK and physical BLE traces:
 * - BC 73 payload 03 -> VoiceActivationStarted (Transitions state to ACTIVE)
 * - BC 59 -> Raw Opus audio frames transmitted
 * - BC 73 payload 0A -> VoiceActivationEnded (Transitions state to IDLE/ENDED)
 */
class HeyCyanVoiceSession(
    events: Flow<HeyCyanEvent>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    constructor(
        driver: RealHeyCyanDriver,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    ) : this(driver.heyCyanEvents, scope)

    enum class State {
        IDLE,
        ACTIVE,
        ENDED
    }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            events.collect { event ->
                when (event) {
                    is HeyCyanEvent.VoiceActivationStarted -> {
                        _state.value = State.ACTIVE
                    }
                    is HeyCyanEvent.VoiceActivationEnded -> {
                        _state.value = State.ENDED
                        _state.value = State.IDLE
                    }
                    else -> {}
                }
            }
        }
    }

    val isActive: Boolean
        get() = _state.value == State.ACTIVE

    fun forceState(newState: State) {
        _state.value = newState
    }
}
