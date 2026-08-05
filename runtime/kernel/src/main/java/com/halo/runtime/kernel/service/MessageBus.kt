package com.halo.runtime.kernel.service

import com.halo.runtime.kernel.AgentMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter

class MessageBus {
    private val _messages = MutableSharedFlow<AgentMessage>(extraBufferCapacity = 100)
    val messages: SharedFlow<AgentMessage> = _messages.asSharedFlow()

    suspend fun publish(message: AgentMessage) {
        _messages.emit(message)
    }

    fun subscribe(agentId: String): kotlinx.coroutines.flow.Flow<AgentMessage> {
        return _messages.filter { it.receiverId == agentId || it.receiverId == null }
    }
}
