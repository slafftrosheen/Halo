package com.halo.runtime.kernel.bus

import com.halo.icl.core.CognitiveObject
import kotlinx.coroutines.flow.Flow

interface SystemBus<T : CognitiveObject> {
    val name: String
    suspend fun publish(sourceProcessId: String, obj: T)
    fun subscribe(subscriberProcessId: String): Flow<T>
}
