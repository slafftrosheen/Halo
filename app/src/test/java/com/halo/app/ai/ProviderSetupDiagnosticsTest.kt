package com.halo.app.ai

import com.halo.sdk.provider.*
import org.junit.Assert.*
import org.junit.Test

class ProviderSetupDiagnosticsTest {
    @Test fun accountAndIntegrationFailuresRemainDistinctAndContentFree() {
        val expected = mapOf(401 to ProviderTestOutcome.AUTH_FAILED, 402 to ProviderTestOutcome.PAYMENT_OR_CREDITS,
            403 to ProviderTestOutcome.PERMISSION_DENIED, 404 to ProviderTestOutcome.MODEL_NOT_FOUND,
            429 to ProviderTestOutcome.RATE_LIMITED, 500 to ProviderTestOutcome.SERVER_ERROR)
        expected.forEach { (code, outcome) ->
            val error = AiProviderException(AiErrorType.UNKNOWN_ERROR, "fixture-secret-body",
                AiFailureDetails(httpStatus = code, retryAfterMs = if (code == 429) 2000 else null, providerCode = "fixture-secret-code"))
            val diagnostic = providerFailure(ProviderTestStage.TEST_GENERATION, error, "api.example.test")
            assertEquals(outcome, diagnostic.outcome)
            assertEquals(code, diagnostic.httpStatus)
            assertFalse(diagnostic.toString().contains("fixture-secret"))
        }
        assertEquals(ProviderTestOutcome.BAD_ENDPOINT, providerFailure(ProviderTestStage.AUTHENTICATE,
            AiProviderException(AiErrorType.UNKNOWN_ERROR, "", AiFailureDetails(httpStatus = 404))).outcome)
    }
    @Test fun generationDoesNotRequireExactSpellingButRequiresAFinalAnswer() {
        val decorated = generationEvidence(AiResponse("HALO.", providerId = "fixture"))
        assertTrue(decorated.transportSuccess); assertTrue(decorated.nonemptyFinal)
        assertFalse(decorated.sentinelExactness); assertTrue(decorated.proven)
        assertFalse(generationEvidence(AiResponse("<think>working</think>", providerId = "fixture")).proven)
        assertFalse(generationEvidence(AiResponse("<think>working", providerId = "fixture")).proven)
        assertFalse(generationEvidence(AiResponse("", providerId = "fixture")).proven)
    }
    @Test fun networkAndProtectedStorageFailuresAreNotAuthenticationFailures() {
        assertEquals(ProviderTestOutcome.DNS_ERROR, providerFailure(ProviderTestStage.AUTHENTICATE, java.net.UnknownHostException()).outcome)
        assertEquals(ProviderTestOutcome.TLS_ERROR, providerFailure(ProviderTestStage.AUTHENTICATE, javax.net.ssl.SSLException("fixture")).outcome)
        assertEquals(ProviderTestOutcome.TIMEOUT, providerFailure(ProviderTestStage.AUTHENTICATE, java.net.SocketTimeoutException()).outcome)
        assertEquals(ProviderTestOutcome.SECRET_STORE_ERROR, providerFailure(ProviderTestStage.SAVE_SECURELY, IllegalStateException("fixture-secret")).outcome)
    }
}
