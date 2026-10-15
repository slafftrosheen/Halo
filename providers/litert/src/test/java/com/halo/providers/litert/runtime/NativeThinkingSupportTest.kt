package com.halo.providers.litert.runtime

import org.junit.Assert.*
import org.junit.Test

class NativeThinkingSupportTest {
    @Test fun supportIsUnavailableUntilNativeMetadataAndFileIdentityMatch() {
        val path = "/fixture-thinking-supported"
        assertNull(LiteRtModelPreflight.thinkingSupport(path, 128, 20))
        LiteRtModelPreflight.rememberThinkingSupport(path, 128, 20, true)
        assertEquals(true, LiteRtModelPreflight.thinkingSupport(path, 128, 20))
        assertNull(LiteRtModelPreflight.thinkingSupport(path, 129, 20))
        assertNull(LiteRtModelPreflight.thinkingSupport(path, 128, 21))
        assertNull(LiteRtModelPreflight.thinkingSupport("/different-artifact", 128, 20))
    }
    @Test fun actualUnsupportedMetadataDoesNotTurnIntoAdvertisedSupport() {
        val path = "/fixture-thinking-unsupported"
        LiteRtModelPreflight.rememberThinkingSupport(path, 128, 20, false)
        assertEquals(false, LiteRtModelPreflight.thinkingSupport(path, 128, 20))
        LiteRtModelPreflight.rememberThinkingSupport(path, 256, 30, true)
        assertNull(LiteRtModelPreflight.thinkingSupport(path, 128, 20))
        assertEquals(true, LiteRtModelPreflight.thinkingSupport(path, 256, 30))
    }
}
