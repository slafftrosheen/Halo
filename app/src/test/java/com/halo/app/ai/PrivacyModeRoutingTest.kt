package com.halo.app.ai

import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PrivacyModeRoutingTest {
    @Test fun selectingCloudProviderCannotBypassPrivateMode() = runTest {
        val mode = MutableStateFlow(PrivacyMode.PRIVATE)
        val delegate = FakeRouter()
        val router = ParentControlledAiRouter(delegate, { mode.value == PrivacyMode.CLOUD }, mode.map { it == PrivacyMode.CLOUD })
        router.selectProvider("cloud")
        try { router.execute(AiRequest(text = "private"), AiContext.EMPTY); fail("Cloud must stay blocked") }
        catch (failure: AiProviderException) { assertTrue(failure.message.orEmpty().contains("Private / Full Power")) }
        assertEquals(0, delegate.cloudCalls)
        mode.value = PrivacyMode.CLOUD
        assertEquals("cloud", router.execute(AiRequest(text = "synthetic"), AiContext.EMPTY).providerId)
        assertEquals(1, delegate.cloudCalls)
    }

    @Test fun privateTransitionCancelsActiveInference() = runTest {
        val mode = MutableStateFlow(PrivacyMode.CLOUD)
        val delegate = FakeRouter().apply { longRunning = true }
        val router = ParentControlledAiRouter(delegate, { mode.value == PrivacyMode.CLOUD }, mode.map { it == PrivacyMode.CLOUD })
        val task = async { router.execute(AiRequest(text = "synthetic"), AiContext.EMPTY) }
        delegate.started.await()
        mode.value = PrivacyMode.PRIVATE
        try { task.await(); fail("Active cloud work must stop") } catch (_: CancellationException) { }
        assertTrue(delegate.cancelled)
    }

    private class FakeRouter : AiProviderRouter {
        override val selectedProviderId = MutableStateFlow<String?>("cloud")
        override val selectedModelId = MutableStateFlow<String?>("model")
        override val routingPolicy = MutableStateFlow(AiRoutingPolicy.EXPLICIT)
        var cloudCalls = 0
        var longRunning = false
        var cancelled = false
        val started = CompletableDeferred<Unit>()
        override fun selectProvider(id: String) { selectedProviderId.value = id }
        override fun selectModel(modelId: String?) { selectedModelId.value = modelId }
        override fun getAllProviders() = emptyList<AiProvider>()
        override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec): AiExecutionTarget = error("Unused")
        override suspend fun getEligibleProvider(request: AiRequest): AiProvider = error("Unused")
        override suspend fun execute(request: AiRequest, context: AiContext) = execute(request, AiWorkloadSpec(), context)
        override suspend fun execute(request: AiRequest, workload: AiWorkloadSpec, context: AiContext): AiResponse {
            if (!workload.allowRemote) throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "No local target configured")
            cloudCalls++; started.complete(Unit)
            if (longRunning) try { awaitCancellation() } finally { cancelled = true }
            return AiResponse("synthetic", providerId = "cloud", modelId = "model")
        }
        override fun start() = Unit
        override fun stop() = Unit
    }
}
