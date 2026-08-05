package com.halo.runtime.kernel.bus

import com.halo.icl.core.CognitiveObject
import com.halo.icl.ontology.Observation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

open class DefaultSystemBus<T : CognitiveObject>(override val name: String) : SystemBus<T> {
    private val _flow = MutableSharedFlow<T>()
    override suspend fun publish(sourceProcessId: String, obj: T) {
        _flow.emit(obj)
    }
    override fun subscribe(subscriberProcessId: String): Flow<T> = _flow
}

class DefaultPerceptionBus : DefaultSystemBus<Observation>("PerceptionBus"), PerceptionBus {
    override val name: String get() = super<DefaultSystemBus>.name
}
class DefaultCognitiveBus : DefaultSystemBus<CognitiveObject>("CognitiveBus"), CognitiveBus {
    override val name: String get() = super<DefaultSystemBus>.name
}
class DefaultExecutionBus : DefaultSystemBus<CognitiveObject>("ExecutionBus"), ExecutionBus {
    override val name: String get() = super<DefaultSystemBus>.name
}
