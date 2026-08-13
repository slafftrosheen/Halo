package com.halo.app.runtime

import com.halo.engines.speech.RecognitionFinished
import com.halo.engines.speech.HandoffStarted
import com.halo.engines.ai.AiProviderRouter
import com.halo.sdk.provider.AiProvider
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.AiContext
import com.halo.sdk.runtime.HaloEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiHandoffOrchestratorTest {

    @Test
    fun `orchestrator publishes HandoffStarted and calls router on RecognitionFinished`() = runTest {
        val eventBus = com.halo.runtime.events.DefaultEventBus()
        val routerCalled = AtomicBoolean(false)
        
        val mockProvider = object : AiProvider {
            override val id = "mock"
            override val name = "Mock"
            override suspend fun getStatus() = ProviderStatus.READY
            override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
            override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
                routerCalled.set(true)
                return AiResponse("OK")
            }
        }
        
        val router = AiProviderRouter(listOf(mockProvider))
        val orchestrator = AiHandoffOrchestrator(eventBus, router, backgroundScope)
        
        orchestrator.start()
        
        val observedEvents = mutableListOf<HaloEvent>()
        backgroundScope.launch {
            eventBus.events().collect {
                observedEvents.add(it)
            }
        }
        
        testScheduler.runCurrent()
        
        // Publish RecognitionFinished
        eventBus.publish(RecognitionFinished())
        
        testScheduler.runCurrent()
        
        assertTrue("HandoffStarted should be published", observedEvents.any { it is HandoffStarted })
        assertEquals("Router should be called", true, routerCalled.get())
    }
}
