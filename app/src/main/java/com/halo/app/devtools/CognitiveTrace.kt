package com.halo.app.devtools

import com.halo.runtime.kernel.bus.PerceptionBus
import com.halo.runtime.kernel.bus.CognitiveBus
import com.halo.runtime.kernel.bus.ExecutionBus
import com.halo.icl.core.CognitiveObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CognitiveTrace(
    private val scope: CoroutineScope,
    private val perceptionBus: PerceptionBus,
    private val cognitiveBus: CognitiveBus,
    private val executionBus: ExecutionBus
) {
    private val _trace = MutableStateFlow<List<CognitiveObject>>(emptyList())
    val trace: StateFlow<List<CognitiveObject>> = _trace

    fun start() {
        scope.launch {
            perceptionBus.subscribe("tracer").collect { obj ->
                _trace.value = _trace.value + obj
            }
        }
        scope.launch {
            cognitiveBus.subscribe("tracer").collect { obj ->
                _trace.value = _trace.value + obj
            }
        }
        scope.launch {
            executionBus.subscribe("tracer").collect { obj ->
                _trace.value = _trace.value + obj
            }
        }
    }
}
