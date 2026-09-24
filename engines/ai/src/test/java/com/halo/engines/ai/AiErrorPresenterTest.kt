package com.halo.engines.ai

import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiFailureDetails
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.Recoverability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structured failure surfacing: Retry-After must survive provider -> router -> UI,
 * and a busy session must read differently from a broken provider.
 */
class AiErrorPresenterTest {

    private val presenter = DefaultAiErrorPresenter()

    private fun failure(
        type: AiErrorType,
        details: AiFailureDetails = AiFailureDetails(),
        message: String = "provider said something technical: {\"error\":{\"code\":\"x\"}}",
    ) = AiProviderException(type = type, message = message, details = details)

    @Test
    fun `rate limit with retry-after seconds reports the duration`() {
        val error = failure(
            AiErrorType.RATE_LIMITED,
            AiFailureDetails(
                retryAfterMs = 42_000L,
                httpStatus = 429,
                providerCode = "rate_limit_exceeded",
                recoverability = Recoverability.RETRY_AFTER,
            ),
        )

        assertEquals(
            "OpenRouter is temporarily rate limited. Try again in about 42 seconds.",
            presenter.message(error, providerLabel = "OpenRouter"),
        )
    }

    @Test
    fun `rate limit retry-after is rounded to minutes once it exceeds a minute`() {
        val error = failure(
            AiErrorType.RATE_LIMITED,
            AiFailureDetails(retryAfterMs = 61_000L, recoverability = Recoverability.RETRY_AFTER),
        )

        assertEquals(
            "OpenRouter is temporarily rate limited. Try again in about 1 minute.",
            presenter.message(error, providerLabel = "OpenRouter"),
        )
    }

    @Test
    fun `rate limit without a trustworthy duration does not invent one`() {
        val error = failure(AiErrorType.RATE_LIMITED)

        assertEquals(
            "OpenRouter is temporarily rate limited. Try again shortly.",
            presenter.message(error, providerLabel = "OpenRouter"),
        )
    }

    @Test
    fun `authentication failure points at the saved key without echoing provider JSON`() {
        val error = failure(
            AiErrorType.AUTHENTICATION_FAILED,
            AiFailureDetails(httpStatus = 401, recoverability = Recoverability.USER_ACTION_REQUIRED),
        )

        val message = presenter.message(error, providerLabel = "OpenRouter")
        assertEquals("OpenRouter rejected the saved API key. Open Intelligence to replace it.", message)
        assertFalse(message.contains("{"))
        assertFalse(message.contains("429"))
    }

    @Test
    fun `session conflict names the occupying activity instead of blaming the model`() {
        val error = failure(
            AiErrorType.SESSION_CONFLICT,
            AiFailureDetails(
                activeSessionId = "bench-7",
                activeMode = "local model benchmark",
                recoverability = Recoverability.SESSION_CONFLICT,
            ),
        )

        assertEquals(
            "A local model benchmark is already using this model. Stop it or wait for it to finish.",
            presenter.message(error, providerLabel = "llama.cpp"),
        )
    }

    @Test
    fun `session conflict without a known mode still reports busy rather than failed`() {
        val error = failure(AiErrorType.SESSION_CONFLICT)

        val message = presenter.message(error, providerLabel = "llama.cpp")
        assertTrue(message.contains("in use by another session"))
        assertFalse(message.contains("failed"))
    }

    @Test
    fun `provider label falls back when the caller does not know the provider`() {
        val error = failure(AiErrorType.NETWORK_UNAVAILABLE)

        assertEquals(
            "The selected provider could not be reached because the network is unavailable.",
            presenter.message(error),
        )
    }

    @Test
    fun `spoken phrases stay short and deterministic`() {
        val cases = listOf(
            failure(AiErrorType.MODEL_NOT_SELECTED) to "No local model is selected.",
            failure(AiErrorType.MODEL_NOT_READY) to "Local model isn't ready yet.",
            failure(AiErrorType.PROVIDER_UNAVAILABLE) to "The provider is unavailable right now.",
            failure(AiErrorType.TIMEOUT) to "The provider took too long to respond.",
            failure(
                AiErrorType.RATE_LIMITED,
                AiFailureDetails(retryAfterMs = 60_000L, recoverability = Recoverability.RETRY_AFTER),
            ) to "That provider is rate limited. Try again in about 1 minute.",
        )

        cases.forEach { (error, expected) ->
            assertEquals(expected, presenter.spoken(error, providerLabel = "OpenRouter"))
        }
    }

    @Test
    fun `spoken phrases never leak raw exception text`() {
        val raw = "OpenRouter API error: 429 — {\"error\":{\"message\":\"upstream exploded\"}}"
        val error = AiProviderException(
            type = AiErrorType.RATE_LIMITED,
            message = raw,
            details = AiFailureDetails(httpStatus = 429),
        )

        val spoken = presenter.spoken(error, providerLabel = "OpenRouter")
        assertFalse(spoken.contains("{"))
        assertFalse(spoken.contains("429"))
        assertTrue(spoken.length < 90)
    }

    @Test
    fun `technical codes are exposed for diagnostics but not for normal messages`() {
        val error = failure(
            AiErrorType.RATE_LIMITED,
            AiFailureDetails(
                retryAfterMs = 61_000L,
                providerCode = "rate_limit_exceeded",
                httpStatus = 429,
                activeMode = "conversation",
                recoverability = Recoverability.RETRY_AFTER,
            ),
        )

        val diagnostics = presenter.diagnostics(error, providerId = "openrouter")
        assertEquals("RATE_LIMITED", diagnostics["ai.error.type"])
        assertEquals("RETRY_AFTER", diagnostics["ai.error.recoverability"])
        assertEquals("openrouter", diagnostics["ai.provider.id"])
        assertEquals("429", diagnostics["ai.http.status"])
        assertEquals("rate_limit_exceeded", diagnostics["ai.provider.code"])
        assertEquals("61000", diagnostics["ai.retry.after.ms"])
        assertEquals("conversation", diagnostics["ai.active.mode"])

        val message = presenter.message(error, providerLabel = "OpenRouter")
        assertFalse(message.contains("rate_limit_exceeded"))
        assertFalse(message.contains("429"))
    }

    @Test
    fun `delivery policy speaks only when the interaction expects speech`() {
        val error = failure(AiErrorType.RATE_LIMITED)

        assertEquals(FailureDelivery.SPOKEN, AiFailureDeliveryPolicy.decide(error, interactionExpectsSpeech = true))
        assertEquals(FailureDelivery.VISUAL_ONLY, AiFailureDeliveryPolicy.decide(error, interactionExpectsSpeech = false))
    }

    @Test
    fun `cancelled generations are never spoken`() {
        val error = failure(AiErrorType.CANCELLED)

        assertEquals(
            FailureDelivery.VISUAL_ONLY,
            AiFailureDeliveryPolicy.decide(error, interactionExpectsSpeech = true),
        )
    }

    @Test
    fun `retry-after text ignores unusable durations`() {
        assertEquals(null, RetryAfterText.describe(null))
        assertEquals(null, RetryAfterText.describe(0L))
        assertEquals(null, RetryAfterText.describe(-5_000L))
        assertEquals("about 1 second", RetryAfterText.describe(1L))
        assertEquals("about 2 minutes", RetryAfterText.describe(90_000L))
    }
}
