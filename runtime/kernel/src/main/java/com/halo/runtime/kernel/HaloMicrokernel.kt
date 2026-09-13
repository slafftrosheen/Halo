package com.halo.runtime.kernel

import com.halo.runtime.kernel.bus.PerceptionBus
import com.halo.runtime.kernel.bus.CognitiveBus
import com.halo.runtime.kernel.bus.ExecutionBus
import com.halo.icl.ontology.Observation
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.Thought
import com.halo.icl.ontology.Plan
import com.halo.icl.ontology.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


enum class HaloInteractionState {
    Idle, Listening, Capturing, Reasoning, Planning, Executing, Speaking
}

class HaloMicrokernel(
    private val scope: CoroutineScope,
    val processManager: ProcessManager,
    val perceptionBus: PerceptionBus,
    val cognitiveBus: CognitiveBus,
    val executionBus: ExecutionBus
) {
    private val _interactionState = MutableStateFlow(HaloInteractionState.Idle)
    val interactionState: StateFlow<HaloInteractionState> = _interactionState

    private var interactionJobs: List<Job> = emptyList()
    private var booted = false

    suspend fun boot() {
        if (booted) return

        startInteractionObservers()
        try {
            processManager.startAll()
            booted = true
        } catch (error: Throwable) {
            stopInteractionObservers()
            throw error
        }
    }

    suspend fun shutdown() {
        if (!booted && interactionJobs.isEmpty()) return

        try {
            processManager.stopAll()
        } finally {
            stopInteractionObservers()
            booted = false
            _interactionState.value = HaloInteractionState.Idle
        }
    }

    fun setInteractionState(state: HaloInteractionState) {
        _interactionState.value = state
    }

    private fun startInteractionObservers() {
        if (interactionJobs.any { it.isActive }) return

        interactionJobs = listOf(
            scope.launch {
                perceptionBus.subscribe("kernel.interaction").collect { obj ->
                    if (obj is Observation) {
                        if (obj.modality.equals("speech_partial", ignoreCase = true)) {
                            _interactionState.value = HaloInteractionState.Listening
                        } else if (obj.modality.equals("WakeWord", ignoreCase = true)) {
                            _interactionState.value = HaloInteractionState.Listening
                        } else if (obj.modality.equals("speech", ignoreCase = true)) {
                            _interactionState.value = HaloInteractionState.Reasoning
                        }
                    }
                }
            },
            scope.launch {
                cognitiveBus.subscribe("kernel.interaction").collect { obj ->
                    when (obj) {
                        is Thought -> _interactionState.value = HaloInteractionState.Reasoning
                        is Goal -> _interactionState.value = HaloInteractionState.Planning
                    }
                }
            },
            scope.launch {
                executionBus.subscribe("kernel.interaction").collect { obj ->
                    when (obj) {
                        is Plan -> _interactionState.value = HaloInteractionState.Executing
                        is Outcome -> {
                            _interactionState.value = if (obj.success) {
                                HaloInteractionState.Speaking
                            } else {
                                HaloInteractionState.Idle
                            }
                        }
                    }
                }
            }
        )
    }

    private fun stopInteractionObservers() {
        interactionJobs.forEach { it.cancel() }
        interactionJobs = emptyList()
    }
}
