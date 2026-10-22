package com.halo.app.lab.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloLabProtocolTest {
    @Test
    fun fixedFastSmokeFixtureMatchesThePublishedV1Protocol() {
        assertEquals("fast-smoke-1-short-ready", HaloLabProtocol.FAST_SMOKE_FIXTURE_ID)
        assertEquals("59e0652c15e4dff7dd7bfebd7cd7c04b478ddf8824995f9a610e13b9c90dfa40", HaloLabProtocol.FAST_SMOKE_FIXTURE_SHA256)
    }
}
