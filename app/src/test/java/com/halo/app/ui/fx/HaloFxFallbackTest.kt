package com.halo.app.ui.fx

import com.halo.app.ui.icons.HaloGlyph
import org.junit.Assert.assertEquals
import org.junit.Test

class HaloFxFallbackTest {
    @Test fun momentAssetsHaveVisibleStaticRepresentations() {
        assertEquals(HaloGlyph.HALO, haloMomentFallback(HaloFxMoment.SUMMON))
        assertEquals(HaloGlyph.MEMORY, haloMomentFallback(HaloFxMoment.REMEMBER))
        assertEquals(HaloGlyph.CLOUD, haloMomentFallback(HaloFxMoment.CLOUD_HANDOFF))
        assertEquals(HaloGlyph.SUCCESS, haloMomentFallback(HaloFxMoment.BLOOM))
    }

    @Test fun toolAndPairingHaveDistinctStaticAssets() {
        assertEquals(HaloGlyph.AUTOMATION, haloStagedFallback("tool_timer"))
        assertEquals(HaloGlyph.OPEN_APP, haloStagedFallback("tool_open_app"))
        assertEquals(HaloGlyph.GLASSES, haloStagedFallback("device_connect"))
        assertEquals(HaloGlyph.TOOL, haloStagedFallback("unknown"))
    }
}
