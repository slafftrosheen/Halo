package com.halo.app.online

import com.halo.sdk.provider.*
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineProviderProblemTest {
    @Test fun billingAuthenticationAndTransportHaveDifferentRecoveryPaths() {
        assertEquals(OnlineProblem.CREDIT_LIMIT, onlineProviderProblem(AiProviderException(
            AiErrorType.PROVIDER_UNAVAILABLE, "provider error", AiFailureDetails(httpStatus = 402))))
        assertEquals(OnlineProblem.AUTHENTICATION, onlineProviderProblem(AiProviderException(
            AiErrorType.AUTHENTICATION_FAILED, "provider error")))
        assertEquals(OnlineProblem.NETWORK, onlineProviderProblem(AiProviderException(
            AiErrorType.NETWORK_ERROR, "reset")))
        assertEquals(OnlineProblem.RATE_LIMIT, onlineProviderProblem(AiProviderException(
            AiErrorType.RATE_LIMITED, "limited")))
    }
}
