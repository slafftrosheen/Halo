package com.halo.providers.litert.models

import org.junit.Assert.*
import org.junit.Test

class SmolLmCandidateTest {
    @Test fun candidatesHaveExactPinsAndMeasuredCapabilitiesRemainUnproven() {
        val a = LiteRtModelCatalog.SMOLLM2_135M_INSTRUCT
        val b = LiteRtModelCatalog.SMOLLM2_360M_INSTRUCT
        assertEquals("749fc23bc8d7ef89db031d075004289c88f41c2f", a.revision)
        assertEquals("507c99cfe6541ba2bcd84818786f7b025935e5e1", b.revision)
        assertEquals(142819328L, a.expectedBytes)
        assertEquals(373719040L, b.expectedBytes)
        assertEquals("ccdc5c85735743f081b7d44ca309cab569f76c0f2f0e8e163449a63721969c37", a.sha256)
        assertEquals("8e2834da211b439751af968ed650febdde5a8cb8d88bc6c1a3059f049caa5c2e", b.sha256)
        assertEquals("SmolLM2_135M_Instruct.litertlm", a.filename)
        assertEquals("SmolLM2_360M_instruct.litertlm", b.filename)
        assertEquals(4096, a.contextWindowTokens)
        assertEquals(8192, b.contextWindowTokens)
        listOf(a, b).forEach {
            assertEquals("Apache-2.0", it.license)
            assertTrue(it.directDownloadAllowed)
            assertTrue(it.supportsText)
            assertFalse(it.supportsVision)
            assertFalse(it.supportsAudio)
            assertFalse(it.supportsTools)
            assertFalse(it.supportsThinking)
            assertEquals(it, LiteRtModelCatalog.bySha256(it.sha256))
        }
    }
}
