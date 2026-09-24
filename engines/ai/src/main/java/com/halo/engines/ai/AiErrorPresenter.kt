package com.halo.engines.ai

import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.Recoverability

/**
 * Renders normalized provider failures for humans.
 *
 * Implementations must read [com.halo.sdk.provider.AiFailureDetails] rather than inspecting
 * exception messages: a rate limit with a known retry duration is not the same user-facing
 * situation as a bare provider outage, and only the structured details carry that distinction.
 */
interface AiErrorPresenter {
    /** Visual presentation, suitable for Home/Intelligence surfaces. */
    fun message(error: AiProviderException, providerLabel: String? = null): String

    /**
     * Short, deterministic phrase for spoken delivery. Never returns raw exception text:
     * a wearable user needs one sentence, not a stack of provider JSON.
     */
    fun spoken(error: AiProviderException, providerLabel: String? = null): String

    /** Technical fields for Activity/diagnostics. Not for normal UI. */
    fun diagnostics(error: AiProviderException, providerId: String? = null): Map<String, String>
}

/** Where a failure should surface. Mirrors the wearable/phone/background delivery split. */
enum class FailureDelivery {
    /** The current interaction expects speech, so answer briefly out loud. */
    SPOKEN,

    /** Keep it visual/Activity only; speaking it would be noise. */
    VISUAL_ONLY,
}

object AiFailureDeliveryPolicy {
    /**
     * Speaker-origin interactions should hear a failure. Background and phone-only failures
     * stay visual so they cannot talk over an unrelated interaction.
     */
    fun decide(error: AiProviderException, interactionExpectsSpeech: Boolean): FailureDelivery {
        if (error.type == AiErrorType.CANCELLED) return FailureDelivery.VISUAL_ONLY
        return if (interactionExpectsSpeech) FailureDelivery.SPOKEN else FailureDelivery.VISUAL_ONLY
    }
}

/** Converts a millisecond budget into the "about 1 minute" style phrasing users can act on. */
object RetryAfterText {
    fun describe(retryAfterMs: Long?): String? {
        val millis = retryAfterMs?.takeIf { it > 0L } ?: return null
        val seconds = (millis + 999L) / 1_000L
        if (seconds < 60L) return "about $seconds ${unit(seconds, "second")}"
        val minutes = ((seconds + 30L) / 60L).coerceAtLeast(1L)
        return "about $minutes ${unit(minutes, "minute")}"
    }

    private fun unit(value: Long, singular: String) = if (value == 1L) singular else "${singular}s"
}

class DefaultAiErrorPresenter : AiErrorPresenter {

    override fun message(error: AiProviderException, providerLabel: String?): String {
        val provider = label(providerLabel) ?: "The selected provider"
        val retry = RetryAfterText.describe(error.details.retryAfterMs)
        return when (error.type) {
            AiErrorType.MODEL_NOT_SELECTED -> "No AI model selected."
            AiErrorType.MODEL_NOT_FOUND -> "Selected AI model could not be found."
            AiErrorType.MODEL_LOAD_FAILED -> "The local AI model could not be loaded."
            AiErrorType.MODEL_NOT_READY -> "The selected AI model is not ready."
            AiErrorType.MODEL_UNAVAILABLE, AiErrorType.LOCAL_MODEL_UNAVAILABLE ->
                "The local AI model is unavailable."
            AiErrorType.AUTHENTICATION_FAILED ->
                "$provider rejected the saved API key. Open Intelligence to replace it."
            AiErrorType.RATE_LIMITED -> if (retry != null) {
                "$provider is temporarily rate limited. Try again in $retry."
            } else {
                "$provider is temporarily rate limited. Try again shortly."
            }
            AiErrorType.SESSION_CONFLICT -> busyMessage(error)
            AiErrorType.PROVIDER_UNAVAILABLE -> if (retry != null) {
                "$provider is temporarily unavailable. Try again in $retry."
            } else {
                "The selected AI provider is unavailable."
            }
            AiErrorType.NETWORK_UNAVAILABLE ->
                "$provider could not be reached because the network is unavailable."
            AiErrorType.NETWORK_ERROR -> "$provider could not be reached over the network."
            AiErrorType.TIMEOUT -> "$provider did not respond in time."
            AiErrorType.EMPTY_RESPONSE -> "The AI returned an empty response."
            AiErrorType.UNSUPPORTED_CAPABILITY -> "This AI provider cannot perform that type of request."
            AiErrorType.INVALID_REQUEST -> "The AI request was invalid."
            AiErrorType.INVALID_RESPONSE -> "The AI provider returned an invalid response format."
            AiErrorType.CONTEXT_TOO_LARGE -> "The conversation context exceeds the model's token limit."
            AiErrorType.CANCELLED -> "AI generation was cancelled."
            AiErrorType.GENERATION_FAILED -> when (error.details.recoverability) {
                Recoverability.RETRY_IMMEDIATELY ->
                    "$provider failed to generate a response. Retrying may work."
                else -> "The AI failed to generate a response."
            }
            else -> when (error.details.recoverability) {
                Recoverability.USER_ACTION_REQUIRED ->
                    "$provider needs attention. Open Intelligence to review its settings."
                else -> "An unexpected AI error occurred."
            }
        }
    }

    override fun spoken(error: AiProviderException, providerLabel: String?): String {
        val retry = RetryAfterText.describe(error.details.retryAfterMs)
        return when (error.type) {
            AiErrorType.MODEL_NOT_SELECTED -> "No local model is selected."
            AiErrorType.MODEL_NOT_FOUND, AiErrorType.MODEL_UNAVAILABLE, AiErrorType.LOCAL_MODEL_UNAVAILABLE ->
                "The local model isn't available."
            AiErrorType.MODEL_LOAD_FAILED -> "The local model didn't load."
            AiErrorType.MODEL_NOT_READY -> "Local model isn't ready yet."
            AiErrorType.AUTHENTICATION_FAILED -> "The API key was rejected."
            AiErrorType.RATE_LIMITED -> if (retry != null) {
                "That provider is rate limited. Try again in $retry."
            } else {
                "That provider is rate limited. Try again shortly."
            }
            AiErrorType.SESSION_CONFLICT -> "The model is busy with another request."
            AiErrorType.NETWORK_UNAVAILABLE, AiErrorType.NETWORK_ERROR -> "I can't reach the provider right now."
            AiErrorType.TIMEOUT -> "The provider took too long to respond."
            AiErrorType.PROVIDER_UNAVAILABLE -> "The provider is unavailable right now."
            AiErrorType.CANCELLED -> "That request was cancelled."
            else -> "That request failed."
        }
    }

    override fun diagnostics(error: AiProviderException, providerId: String?): Map<String, String> = buildMap {
        put("ai.error.type", error.type.name)
        put("ai.error.recoverability", error.details.recoverability.name)
        providerId?.let { put("ai.provider.id", it) }
        error.details.httpStatus?.let { put("ai.http.status", it.toString()) }
        error.details.providerCode?.let { put("ai.provider.code", it) }
        error.details.retryAfterMs?.let { put("ai.retry.after.ms", it.toString()) }
        error.details.activeSessionId?.let { put("ai.active.session.id", it) }
        error.details.activeMode?.let { put("ai.active.mode", it) }
    }

    private fun busyMessage(error: AiProviderException): String {
        val mode = error.details.activeMode?.trim()?.takeIf { it.isNotEmpty() }
        return if (mode != null) {
            "A $mode is already using this model. Stop it or wait for it to finish."
        } else {
            "The model is currently in use by another session. Wait for it to finish and try again."
        }
    }

    private fun label(providerLabel: String?): String? = providerLabel?.trim()?.takeIf { it.isNotEmpty() }
}
