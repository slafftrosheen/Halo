package com.halo.app.online

import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiProviderException

/** Preserve actionable provider failures instead of calling billing or authentication a sharing problem. */
fun onlineProviderProblem(error: AiProviderException): OnlineProblem = when {
    error.details.httpStatus == 402 -> OnlineProblem.CREDIT_LIMIT
    error.details.httpStatus in setOf(401, 403) || error.type == AiErrorType.AUTHENTICATION_FAILED -> OnlineProblem.AUTHENTICATION
    error.type == AiErrorType.RATE_LIMITED -> OnlineProblem.RATE_LIMIT
    error.type in setOf(AiErrorType.NETWORK_ERROR, AiErrorType.NETWORK_UNAVAILABLE, AiErrorType.TIMEOUT) -> OnlineProblem.NETWORK
    error.type == AiErrorType.INVALID_RESPONSE -> OnlineProblem.INVALID_RESPONSE
    else -> OnlineProblem.UNAVAILABLE
}
