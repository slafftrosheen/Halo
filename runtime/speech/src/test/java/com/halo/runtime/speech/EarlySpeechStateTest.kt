package com.halo.runtime.speech

import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class EarlySpeechStateTest {
    @Test fun earlyAudibleStartSurvivesGenerationCompletionUntilExactPlaybackTerminal() = runBlocking {
        val events = MutableSharedFlow<HaloEvent>(extraBufferCapacity = 16)
        val bus = object : EventBus {
            override suspend fun publish(event: HaloEvent) { events.emit(event) }
            override fun events() = events
            override fun <T : HaloEvent> observe(type: Class<T>) = emptyFlow<T>()
        }
        val commands = object : CommandBus {
            override suspend fun dispatch(command: RuntimeCommand) = Unit
            override fun commands() = emptyFlow<RuntimeCommand>()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = SpeechSessionManager(bus, commands, scope)
        try {
            manager.start()
            bus.publish(CognitiveRequestStarted(requestId = "request"))
            bus.publish(TtsStarted(transactionId = "request"))
            assertEquals(SpeechSessionState.Speaking, manager.sessionState.value)
            bus.publish(CognitiveCompleted(requestId = "request", response = "Done"))
            assertEquals(SpeechSessionState.Speaking, manager.sessionState.value)
            bus.publish(TtsCompleted(transactionId = "stale")); assertEquals(SpeechSessionState.Speaking, manager.sessionState.value)
            bus.publish(TtsCompleted(transactionId = "request")); assertEquals(SpeechSessionState.Idle, manager.sessionState.value)
        } finally { manager.stop(); scope.cancel() }
    }
}
