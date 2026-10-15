package com.halo.app.ai

import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.safeProviderErrorCode
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException

enum class ProviderTestStage { SAVE_SECURELY, AUTHENTICATE, FETCH_MY_MODELS, TEST_GENERATION }
enum class ProviderTestOutcome {
    SAVED_UNTESTED, CHECKING, READY_AUTH, MODELS_AVAILABLE, GENERATION_PROVEN,
    AUTH_FAILED, PERMISSION_DENIED, PAYMENT_OR_CREDITS, RATE_LIMITED, MODEL_NOT_FOUND,
    TIMEOUT, TLS_ERROR, DNS_ERROR, OFFLINE, BAD_ENDPOINT, PROTOCOL_ERROR, CANCELLED,
    SERVER_ERROR, SECRET_STORE_ERROR, CONSENT_REQUIRED, MODEL_REQUIRED, NO_FINAL_ANSWER,
}
data class ProviderTestDiagnostic(
    val stage: ProviderTestStage,
    val outcome: ProviderTestOutcome,
    val httpStatus: Int? = null,
    val providerCode: String? = null,
    val retryAfterMs: Long? = null,
    val endpointHost: String? = null,
    val checkedAtMs: Long = System.currentTimeMillis(),
    val checkId: String = java.util.UUID.randomUUID().toString(),
) {
    fun summary(): String = buildString {
        append(stage.name).append(" · ").append(outcome.name)
        httpStatus?.let { append(" · HTTP ").append(it) }
        providerCode?.let { append(" · ").append(it) }
        endpointHost?.let { append(" · ").append(it) }
        append(" · age ").append(((System.currentTimeMillis() - checkedAtMs).coerceAtLeast(0)) / 1000).append("s")
        append(" · check ").append(checkId)
        retryAfterMs?.let { append(" · retry after ").append(it / 1000).append("s") }
    }
}
/** Never retain exception messages, provider response bodies, prompts, keys or full URLs. */
internal fun providerFailure(stage: ProviderTestStage, error: Throwable, host: String? = null): ProviderTestDiagnostic {
    val failure = error as? AiProviderException
    val http = failure?.details?.httpStatus
    val outcome = when {
        error is kotlinx.coroutines.TimeoutCancellationException -> ProviderTestOutcome.TIMEOUT
        error is CancellationException -> ProviderTestOutcome.CANCELLED
        http == 401 -> ProviderTestOutcome.AUTH_FAILED
        http == 402 -> ProviderTestOutcome.PAYMENT_OR_CREDITS
        http == 403 && failure?.details?.providerCode in setOf("insufficient_quota", "billing_hard_limit_reached") -> ProviderTestOutcome.PAYMENT_OR_CREDITS
        http == 403 -> ProviderTestOutcome.PERMISSION_DENIED
        http == 404 -> if (stage == ProviderTestStage.TEST_GENERATION) ProviderTestOutcome.MODEL_NOT_FOUND else ProviderTestOutcome.BAD_ENDPOINT
        http == 429 -> ProviderTestOutcome.RATE_LIMITED
        http != null && http >= 500 -> ProviderTestOutcome.SERVER_ERROR
        error is com.halo.sdk.provider.CredentialStorageException -> ProviderTestOutcome.SECRET_STORE_ERROR
        error is java.net.SocketTimeoutException || failure?.type == AiErrorType.TIMEOUT -> ProviderTestOutcome.TIMEOUT
        error is SSLException || error.cause is SSLException -> ProviderTestOutcome.TLS_ERROR
        error is UnknownHostException || error.cause is UnknownHostException -> ProviderTestOutcome.DNS_ERROR
        failure?.type in setOf(AiErrorType.NETWORK_ERROR, AiErrorType.NETWORK_UNAVAILABLE) -> ProviderTestOutcome.OFFLINE
        stage == ProviderTestStage.SAVE_SECURELY && error is IllegalArgumentException -> ProviderTestOutcome.BAD_ENDPOINT
        stage == ProviderTestStage.SAVE_SECURELY -> ProviderTestOutcome.SECRET_STORE_ERROR
        failure?.type == AiErrorType.AUTHENTICATION_FAILED -> ProviderTestOutcome.AUTH_FAILED
        failure?.type == AiErrorType.RATE_LIMITED -> ProviderTestOutcome.RATE_LIMITED
        failure?.type == AiErrorType.MODEL_NOT_FOUND -> ProviderTestOutcome.MODEL_NOT_FOUND
        failure?.type == AiErrorType.MODEL_NOT_SELECTED -> ProviderTestOutcome.MODEL_REQUIRED
        failure?.type == AiErrorType.EMPTY_RESPONSE -> ProviderTestOutcome.NO_FINAL_ANSWER
        error is IllegalArgumentException -> ProviderTestOutcome.BAD_ENDPOINT
        else -> ProviderTestOutcome.PROTOCOL_ERROR
    }

    return ProviderTestDiagnostic(stage, outcome, http, safeProviderErrorCode(failure?.details?.providerCode),
        failure?.details?.retryAfterMs, host)
}
data class ProviderGenerationEvidence(val transportSuccess: Boolean, val nonemptyFinal: Boolean, val sentinelExactness: Boolean, val protocolSuccess: Boolean = true) {
    val proven: Boolean get() = transportSuccess && nonemptyFinal && protocolSuccess
}
internal fun generationEvidence(response: AiResponse): ProviderGenerationEvidence {
    val final = if (response.text.contains("<think>") && !response.text.contains("</think>")) "" else
        response.text.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "").trim()
    return ProviderGenerationEvidence(true, final.isNotEmpty(), final == "HALO", response.toolCalls.isEmpty())
}
