package com.halo.providers.litert.runtime

import org.junit.Assert.*
import org.junit.Test

class LiteRtContextPresetPolicyTest {
    @Test fun onlyBoundedKnownArtifactsMayReceiveDeveloperPresets() {
        assertEquals(listOf(1024, 2048, 4096), LiteRtContextPresetPolicy.choicesFor(4096))
        assertEquals(listOf(1024, 2048, 4096, 8192), LiteRtContextPresetPolicy.choicesFor(8192))
        assertTrue(LiteRtContextPresetPolicy.isAllowed(null, 4096))
        assertTrue(LiteRtContextPresetPolicy.isAllowed(2048, 4096))
        assertFalse(LiteRtContextPresetPolicy.isAllowed(8192, 4096))
        assertFalse(LiteRtContextPresetPolicy.isAllowed(256, 4096))
        assertFalse(LiteRtContextPresetPolicy.isAllowed(-1, 4096))
    }
}
