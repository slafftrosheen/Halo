package com.halo.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.app.di.ManualDependencyGraph
import com.halo.sdk.runtime.ObservationCreated
import com.halo.runtime.cognition.OutcomePublished
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
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
        com.halo.app.di.initializeUnitTestWorkManager(context)
        ManualDependencyGraph.init(context)

        // Replace SpeechSynthesisProvider with a stub to avoid Robolectric TTS timeout.
        ManualDependencyGraph.providerGraph.replaceProviders { providers ->
            val testInference = object : com.halo.sdk.provider.AiInferenceProvider {
                override val id = "test-ai"
                override val displayName = "Test AI"
                override val isLocal = true
                override val status = kotlinx.coroutines.flow.MutableStateFlow(
                    com.halo.sdk.provider.ProviderStatus.READY
                )

                override suspend fun getCapabilities() = setOf(
                    com.halo.sdk.provider.AiCapability.TEXT,
                    com.halo.sdk.provider.AiCapability.AUDIO,
                    com.halo.sdk.provider.AiCapability.MULTIMODAL
                )

                override suspend fun generate(
                    request: com.halo.sdk.provider.AiRequest,
                    context: com.halo.sdk.provider.AiContext
                ): com.halo.sdk.provider.AiResponse {
                    error("A native media command must never invoke inference")
                }

                override fun stream(
                    request: com.halo.sdk.provider.AiRequest,
                    context: com.halo.sdk.provider.AiContext
                ) = kotlinx.coroutines.flow.emptyFlow<com.halo.sdk.provider.AiStreamEvent>()

                override suspend fun listModels() = emptyList<com.halo.sdk.provider.AiModel>()
            }

            providers.copy(
                speechSynthesis = object : com.halo.sdk.provider.SpeechSynthesisProvider {
                    override suspend fun synthesize(
                        request: com.halo.sdk.provider.SpeechRequest
                    ): com.halo.sdk.provider.SpeechResult {
                        return com.halo.sdk.provider.SpeechResult(
                            durationMillis = 1000L,
                            successful = true
                        )
                    }

                    override fun stream(
                        text: String
                    ): kotlinx.coroutines.flow.Flow<com.halo.sdk.host.AudioFrame> =
                        kotlinx.coroutines.flow.emptyFlow()

                    override suspend fun health(): Boolean = true
                },
                inference = testInference,
                aiProviders = listOf(testInference)
            )
        }
    }

    @Test
    fun testCognitiveLoop_EndToEnd() = runBlocking {
        withTimeout(30000L) {
            val graph = ManualDependencyGraph

            // Initialize and start the engines that form the cognitive loop.
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

            val engineGraph = graph.engineGraph
            engineGraph.interpretationEngine.initialize(context)
            engineGraph.reasoningEngine.initialize(context)
            engineGraph.planningEngine.initialize(context)
            engineGraph.executionEngine.initialize(context)

            engineGraph.interpretationEngine.start()
            engineGraph.reasoningEngine.start()
            engineGraph.planningEngine.start()
            engineGraph.executionEngine.start()
            graph.aiProviderRouter.start()

            delay(100)

            graph.cognitiveSession // Native interpretation is owned by the shared session, not the legacy planner.
            val eventBus = graph.eventBus
            val outcomeDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                val outcomeEvent = eventBus.observe(com.halo.sdk.runtime.AssistantResponsePublished::class.java).first()
                assertTrue(
                    outcomeEvent.text.isNotBlank()
                )
            }

            val observation = com.halo.sdk.runtime.Observation(
                payload = com.halo.engines.speech.SpeechRecognizedPayload(
                    transcript = "play music",
                    language = "en-US"
                ),
                source = com.halo.sdk.runtime.Source.MICROPHONE,
                modality = com.halo.sdk.runtime.Modality.AUDIO
            )

            eventBus.publish(ObservationCreated(observation = observation))
            outcomeDeferred.await()

            graph.audioPerceptionEngine.stop()
            engineGraph.interpretationEngine.stop()
            engineGraph.reasoningEngine.stop()
            engineGraph.planningEngine.stop()
            engineGraph.executionEngine.stop()
            graph.aiProviderRouter.stop()
        }
    }
}
