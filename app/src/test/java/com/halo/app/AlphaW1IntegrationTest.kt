package com.halo.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.app.di.ManualDependencyGraph
import com.halo.sdk.runtime.ObservationCreated
import com.halo.sdk.runtime.OutcomePublished
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AlphaW1IntegrationTest {

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ManualDependencyGraph.init(context)
    }

    @Test
    fun testCognitiveLoop_EndToEnd() = runBlocking {
        withTimeout(30000L) {
            val graph = ManualDependencyGraph

        // 1. Initialize and start engines/orchestrators
        // For testing the core loop, we need RuntimeOrchestrator and AudioPerceptionEngine
        val context = com.halo.sdk.runtime.EngineContext(
            runtime = object : com.halo.sdk.runtime.RuntimeContext {
                override val eventBus = graph.eventBus
                override val commandBus = graph.commandBus
                override val queryBus = graph.queryBus
                override val registry = graph.registry
                override val logger = graph.logger
                override val scheduler = graph.scheduler
            },
            registry = graph.registry
        )
        
        graph.audioPerceptionEngine.initialize(context)
        graph.audioPerceptionEngine.start()
        
        // Ensure the orchestrator is started (happens in graph by lazy)
        val orchestrator = graph.runtimeOrchestrator
        
        delay(100)

        // 2. We can simulate an incoming observation directly on eventBus
        val eventBus = graph.eventBus
        
        // Listen for OutcomePublished
        val outcomeDeferred = launch {
            val outcomeEvent = eventBus.observe(OutcomePublished::class.java).first()
            assertTrue(outcomeEvent.outcome.contains("Success") || outcomeEvent.outcome.contains("Failure"))
        }

        val observation = com.halo.sdk.runtime.Observation(
            payload = com.halo.engines.speech.SpeechRecognizedPayload(
                transcript = "Hello Halo",
                language = "en-US"
            ),
            source = com.halo.sdk.runtime.Source.MICROPHONE,
            modality = com.halo.sdk.runtime.Modality.AUDIO
        )
        
        eventBus.publish(ObservationCreated(observation = observation))
        
        // 4. Wait for OutcomePublished
        outcomeDeferred.join()
        
        graph.audioPerceptionEngine.stop()
        }
    }
}
