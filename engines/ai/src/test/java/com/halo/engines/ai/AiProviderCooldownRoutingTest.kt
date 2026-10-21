package com.halo.engines.ai

import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiRoutingPolicy
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.ProviderCooldown
import com.halo.sdk.provider.ProviderReadiness
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.Recoverability
import com.halo.sdk.provider.TargetRejectionReason
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A provider that is temporarily rate limited is busy, not broken, and it is still the provider the
 * user chose. Rate limiting must not become an implicit routing decision.
 */
class AiProviderCooldownRoutingTest {

    private class FakeStorage : Storage {
        private val data = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = data[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }

    class CooldownProvider(
        override val id: String,
        override val displayName: String,
        override val isLocal: Boolean,
        initialStatus: ProviderStatus = ProviderStatus.READY,
        /** When set, the provider derives its own status the way a real provider does. */
        private val cooldownUntilMs: Long? = null,
    ) : AiInferenceProvider {
        private val flow = MutableStateFlow(initialStatus)
        override val status: StateFlow<ProviderStatus> = flow
        var generateCalls = 0

        fun setStatus(value: ProviderStatus) { flow.value = value }

        /** A provider recomputes this on every probe; an elapsed window means ready again. */
        override suspend fun getStatus(): ProviderStatus {
            val until = cooldownUntilMs ?: return flow.value
            return if (until > System.currentTimeMillis()) coolingStatus(until) else ProviderStatus.READY
        }
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            generateCalls++
            return AiResponse("ok", providerId = id, modelId = request.modelId)
        }
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
        override suspend fun listModels() = listOf(
            AiModel(if (id == "openrouter") "openrouter/free" else "test-model", "Test Model", id, setOf(AiCapability.TEXT), 4096, false, false)
        )
    }

    private val activeCooldownUntil = System.currentTimeMillis() + 60_000L

    @Test
    fun `a cooling provider stays selected and does not hand off to a ready alternative`() = runTest {
        val storage = FakeStorage()
        val cloud = coolingProvider("openrouter", "OpenRouter", isLocal = false, untilEpochMs = activeCooldownUntil)
        val local = CooldownProvider("llamacpp", "llama.cpp", isLocal = true)
        val router = DefaultAiProviderRouter(listOf(cloud, local), storage)

        router.selectProvider("openrouter")

        val thrown = runCatching { router.resolveExecutionTarget(AiRequest(text = "hello")) }.exceptionOrNull()

        val error = thrown as? AiProviderException
        assertEquals(AiErrorType.RATE_LIMITED, error?.type)
        assertEquals(Recoverability.RETRY_AFTER, error?.details?.recoverability)
        assertTrue((error?.details?.retryAfterMs ?: 0L) > 0L)

        // The user's choice is untouched, and no substitute provider was used.
        assertEquals("openrouter", router.selectedProviderId.value)
        assertEquals(AiRoutingPolicy.EXPLICIT, router.routingPolicy.value)
        assertNull(router.lastRoutingDecision.value?.resolvedProviderId)
        assertEquals(TargetRejectionReason.PROVIDER_COOLDOWN, router.lastRoutingDecision.value?.rejectedTargets?.single()?.reason)
    }

    @Test
    fun `repeated attempts during a cooldown never reach the provider`() = runTest {
        val storage = FakeStorage()
        val cloud = coolingProvider("openrouter", "OpenRouter", isLocal = false, untilEpochMs = activeCooldownUntil)
        val router = DefaultAiProviderRouter(listOf(cloud), storage)
        router.selectProvider("openrouter")

        repeat(5) {
            runCatching { router.execute(AiRequest(text = "hello"), AiContext.EMPTY) }
        }

        assertEquals("cooldown must short-circuit before invoking the provider", 0, cloud.generateCalls)
    }

    @Test
    fun `an expired cooldown allows the provider again without a restart`() = runTest {
        val expired = System.currentTimeMillis() - 1_000L
        val storage = FakeStorage()
        val cloud = coolingProvider("openrouter", "OpenRouter", isLocal = false, untilEpochMs = expired)
        val router = DefaultAiProviderRouter(listOf(cloud), storage)
        router.selectProvider("openrouter")

        val response = router.execute(AiRequest(text = "hello"), AiContext.EMPTY)

        assertEquals("openrouter", response.providerId)
        assertEquals(1, cloud.generateCalls)
    }

    @Test
    fun `a provider that clears its own cooldown is used again`() = runTest {
        val storage = FakeStorage()
        val cloud = CooldownProvider("openrouter", "OpenRouter", isLocal = false, initialStatus = coolingStatus(activeCooldownUntil))
        val router = DefaultAiProviderRouter(listOf(cloud), storage)
        router.selectProvider("openrouter")

        runCatching { router.execute(AiRequest(text = "hello"), AiContext.EMPTY) }
        assertEquals(0, cloud.generateCalls)

        // The provider's own clock rolls the window over; no app restart is involved.
        cloud.setStatus(ProviderStatus.READY)

        assertEquals("openrouter", router.execute(AiRequest(text = "hello"), AiContext.EMPTY).providerId)
        assertEquals(1, cloud.generateCalls)
    }

    @Test
    fun `local only policy never escapes to cloud while the local provider cools down`() = runTest {
        val storage = FakeStorage().apply {
            putString("active_ai_provider", "llamacpp")
            putString("ai_routing_policy", "LOCAL_ONLY")
        }
        val local = coolingProvider("llamacpp", "llama.cpp", isLocal = true, untilEpochMs = activeCooldownUntil)
        val cloud = CooldownProvider("openrouter", "OpenRouter", isLocal = false)
        val router = DefaultAiProviderRouter(listOf(local, cloud), storage)

        assertEquals(AiRoutingPolicy.LOCAL_ONLY, router.routingPolicy.value)

        val thrown = runCatching { router.resolveExecutionTarget(AiRequest(text = "hello")) }.exceptionOrNull()

        if (thrown is AiProviderException) {
            assertTrue(
                "a cooling local provider must never be resolved as a cloud provider",
                thrown.type != AiErrorType.MODEL_NOT_READY || router.lastRoutingDecision.value?.resolvedProviderId == null,
            )
        }
        assertNull(router.lastRoutingDecision.value?.resolvedProviderId)
        assertTrue(
            router.lastRoutingDecision.value?.rejectedTargets.orEmpty()
                .any { it.providerId == "openrouter" && it.reason == TargetRejectionReason.POLICY_EXCLUDED },
        )
    }

    @Test
    fun `explicit selection fails loudly rather than substituting silently`() = runTest {
        val storage = FakeStorage()
        val cloud = CooldownProvider("openrouter", "OpenRouter", isLocal = false, initialStatus = ProviderStatus.UNCONFIGURED)
        val local = CooldownProvider("llamacpp", "llama.cpp", isLocal = true)
        val router = DefaultAiProviderRouter(listOf(cloud, local), storage)
        router.selectProvider("openrouter")

        try {
            router.execute(AiRequest(text = "hello"), AiContext.EMPTY)
            fail("Expected the explicitly selected provider to fail rather than substitute")
        } catch (e: AiProviderException) {
            assertEquals(AiErrorType.AUTHENTICATION_FAILED, e.type)
        }
        assertEquals(0, local.generateCalls)
    }
}

private fun coolingStatus(untilEpochMs: Long) = ProviderStatus(
    state = ProviderStatus.State.STANDBY,
    message = "OpenRouter is rate limited. Try again in about 1 minute.",
    readiness = ProviderReadiness.Failed("Provider cooldown"),
    rejectionReason = TargetRejectionReason.PROVIDER_COOLDOWN,
    cooldown = ProviderCooldown(untilEpochMs, AiErrorType.RATE_LIMITED),
)

private fun coolingProvider(
    id: String,
    displayName: String,
    isLocal: Boolean,
    untilEpochMs: Long?,
) = AiProviderCooldownRoutingTest.CooldownProvider(
    id = id,
    displayName = displayName,
    isLocal = isLocal,
    cooldownUntilMs = untilEpochMs,
)

