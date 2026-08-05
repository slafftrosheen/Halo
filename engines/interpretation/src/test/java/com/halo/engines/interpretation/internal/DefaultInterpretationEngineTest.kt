package com.halo.engines.interpretation.internal

import com.halo.engines.interpretation.rules.ContactRule
import com.halo.engines.interpretation.rules.SpeechRule
import com.halo.engines.interpretation.rules.TimeRule
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.CallIntent
import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.blackboard.EntityKnowledge
import com.halo.runtime.cognition.blackboard.IntentKnowledge
import com.halo.runtime.cognition.blackboard.InterpretationKnowledge
import com.halo.runtime.cognition.blackboard.PlaceEntity
import com.halo.runtime.cognition.blackboard.SpeechKnowledge
import com.halo.runtime.cognition.context.DefaultContextAssembler
import com.halo.runtime.cognition.workingmemory.WorkingMemory
import com.halo.runtime.services.Services
import com.halo.sdk.runtime.CommandBus
import com.halo.sdk.runtime.EngineContext
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloEvent
import com.halo.sdk.runtime.HaloLogger
import com.halo.sdk.runtime.Modality
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.Query
import com.halo.sdk.runtime.QueryBus
import com.halo.sdk.runtime.RuntimeCommand
import com.halo.sdk.runtime.RuntimeContext
import com.halo.sdk.runtime.RuntimeScheduler
import com.halo.sdk.runtime.ServiceKey
import com.halo.sdk.runtime.ServiceRegistry
import com.halo.sdk.runtime.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultInterpretationEngineTest {
    @Test
    fun speechObservationPublishesSemanticKnowledgeOnly(): Unit = runBlocking {
        val blackboard = DefaultBlackboard()
        val eventBus = TestEventBus()
        val engine = DefaultInterpretationEngine(
            pipeline = RulePipeline(
                listOf(ContactRule(), TimeRule(), SpeechRule())
            ),
            blackboard = blackboard,
            contextAssembler = DefaultContextAssembler(
                blackboard = blackboard,
                workingMemory = EmptyWorkingMemory()
            )
        )
        engine.initialize(engineContext(blackboard, eventBus))

        val result = engine.interpret(
            Observation(
                modality = Modality.AUDIO,
                source = Source.MICROPHONE,
                payload = SpeechRecognizedPayload("Call Sarah", "en"),
                confidence = 0.9f,
                timestamp = 1_000L
            )
        )

        assertEquals(CallIntent, result.interpretation.intent)
        assertNotNull(blackboard.latest(SpeechKnowledge::class))
        assertNotNull(blackboard.latest(IntentKnowledge::class))
        assertNotNull(blackboard.latest(EntityKnowledge::class))
        assertNotNull(blackboard.latest(InterpretationKnowledge::class))
        assertNull(blackboard.latest(com.halo.runtime.cognition.blackboard.ObservationPayload::class))
    }

    private class EmptyWorkingMemory : WorkingMemory {
        override suspend fun currentGoal(): com.halo.runtime.cognition.Goal? = null
        override suspend fun activeTask(): com.halo.runtime.cognition.Task? = null
        override suspend fun latestObservation(): Observation? = null
        override suspend fun latestInterpretation():
            com.halo.runtime.cognition.blackboard.Interpretation? = null

        override suspend fun focusedPerson():
            com.halo.runtime.cognition.blackboard.PersonEntity? = null

        override suspend fun currentLocation(): PlaceEntity? = null
    }

    private class TestEventBus : EventBus {
        override suspend fun publish(event: HaloEvent) = Unit
        override fun events(): Flow<HaloEvent> = emptyFlow()
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
    }

    private fun engineContext(
        blackboard: Blackboard,
        bus: EventBus
    ): EngineContext {
        val serviceRegistry = object : ServiceRegistry {
            override fun <T : Any> register(key: ServiceKey<T>, service: T) = Unit

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> resolve(key: ServiceKey<T>): T {
                if (key == Services.Blackboard) {
                    return blackboard as T
                }
                if (key == Services.EventBus) {
                    return bus as T
                }
                error("Unexpected service: ${key.name}")
            }
        }
        val runtime = object : RuntimeContext {
            override val eventBus: EventBus = bus
            override val commandBus: CommandBus = object : CommandBus {
                override suspend fun dispatch(command: RuntimeCommand) = Unit
                override fun commands(): Flow<RuntimeCommand> = emptyFlow()
            }
            override val queryBus: QueryBus = object : QueryBus {
                override suspend fun <R> execute(query: Query<R>): R =
                    error("Queries are not used by this test")
            }
            override val scheduler: RuntimeScheduler = object : RuntimeScheduler {
                override val io = Dispatchers.Default
                override val compute = Dispatchers.Default
                override val realtime = Dispatchers.Default
                override val ui = Dispatchers.Default
            }
            override val registry: ServiceRegistry = serviceRegistry
            override val logger: HaloLogger = object : HaloLogger {
                override fun trace(category: String, message: String) = Unit
                override fun debug(category: String, message: String) = Unit
                override fun info(category: String, message: String) = Unit
                override fun warn(category: String, message: String) = Unit
                override fun error(category: String, message: String, throwable: Throwable?) = Unit
            }
        }
        return EngineContext(serviceRegistry, runtime)
    }
}
