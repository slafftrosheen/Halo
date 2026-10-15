package com.halo.sdk.provider

import org.junit.Assert.*
import org.junit.Test

class ProviderHttpDiagnosticsTest {
    @Test fun retryEvidenceIsFiniteBoundedAndSupportsHttpDates() {
        assertEquals(2000L, providerRetryAfterMs("2"))
        assertEquals(86_400_000L, providerRetryAfterMs("999999999999"))
        assertNull(providerRetryAfterMs("NaN"))
        assertNull(providerRetryAfterMs("Infinity"))
        assertNull(providerRetryAfterMs("-1"))
        assertNull(providerRetryAfterMs("fixture-secret"))
        assertEquals(2000L, providerRetryAfterMs("Thu, 15 Oct 2026 06:00:02 GMT", 1792044000000L))
    }
    @Test fun diagnosticsOnlyRetainRecognizedCodes() {
        assertEquals("invalid_api_key", safeProviderErrorCode("invalid_api_key"))
        assertEquals("RESOURCE_EXHAUSTED", safeProviderErrorCode("RESOURCE_EXHAUSTED"))
        assertEquals("403", safeProviderErrorCode("403"))
        assertNull(safeProviderErrorCode("fixture-secret-code"))
        assertNull(safeProviderErrorCode("123456789"))
    }
}
