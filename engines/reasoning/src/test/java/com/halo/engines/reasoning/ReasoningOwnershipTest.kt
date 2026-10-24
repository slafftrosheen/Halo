package com.halo.engines.reasoning

import com.halo.cognition.language.*
import com.halo.engines.reasoning.service.GoalManager
import com.halo.engines.reasoning.strategy.DefaultReasoningStrategy
import com.halo.runtime.cognition.GoalCreated
import com.halo.runtime.cognition.blackboard.*
import com.halo.sdk.runtime.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import org.junit.Assert.*
import org.junit.Test

class ReasoningOwnershipTest {
    @Test fun toolIntentHasOneOwnerAndCannotCreateALegacyCommunicationGoal() = runBlocking {
        val board = DefaultBlackboard()
        val events = mutableListOf<HaloEvent>()
        val bus = object : EventBus {
            override suspend fun publish(event: HaloEvent) { events += event }
            override fun events(): Flow<HaloEvent> = emptyFlow()
            override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
        }
        val registry = object : ServiceRegistry {
            override fun <T : Any> register(key: ServiceKey<T>, service: T) = error("Unexpected service registration")
            override fun <T : Any> resolve(key: ServiceKey<T>): T = error("Unexpected service lookup")
        }
        val runtime = object : RuntimeContext {
            override val eventBus = bus
            override val commandBus: CommandBus get() = error("Reasoning must not dispatch host commands")
            override val queryBus: QueryBus get() = error("Reasoning must not query host services")
            override val registry = registry
            override val scheduler = object : RuntimeScheduler {
                override val io = Dispatchers.Unconfined
                override val compute = Dispatchers.Unconfined
                override val realtime = Dispatchers.Unconfined
                override val ui = Dispatchers.Unconfined
            }
            override val logger = object : RuntimeLogger {
                override fun trace(category: String, message: String) = Unit
                override fun debug(category: String, message: String) = Unit
                override fun info(category: String, message: String) = Unit
                override fun warn(category: String, message: String) = Unit
                override fun error(category: String, message: String, throwable: Throwable?) = Unit
            }
        }
        val engine = ReasoningEngine(DefaultReasoningStrategy(GoalManager()), board, bus)
        engine.initialize(EngineContext(registry, runtime))
        engine.start()
        suspend fun publish(intent: Intent, entities: List<Entity> = emptyList()) {
            board.publish(Knowledge(KnowledgeId(UUID.randomUUID().toString()),
                InterpretationKnowledge(Interpretation(transcript = "Synthetic ownership request", intent = intent,
                    entities = entities, confidence = 1f)), Confidence(1f, 1f, 1f), KnowledgeSource.USER, Clock.System.now()))
            yield()
        }
        try {
            // Positive control proves the stream is live and native deterministic work remains owned.
            publish(OpenIntent, listOf(ApplicationEntity("Calculator", 1f)))
            assertEquals(1, events.filterIsInstance<GoalCreated>().size)
            events.clear()
            publish(ToolActionIntent(ToolActionReason.ACTION_INTENT))
            publish(ToolActionIntent(ToolActionReason.WORKSPACE_LOOKUP))
            publish(QuestionIntent)
            publish(ConversationIntent)
            assertTrue("CognitiveSession-owned intents must not create a second executable goal",
                events.filterIsInstance<GoalCreated>().isEmpty())
        } finally {
            engine.stop()
        }
    }
}
