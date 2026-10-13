package com.halo.app.ai

import org.junit.Assert.*
import org.junit.Test

class MobileModelAdviceTest {
    private val gib = 1024L * 1024 * 1024

    @Test fun adviceIsDeviceScopedAndNeverActsAsProviderQualification() {
        val redmi = MobileModelAdvice.forModel("qwen3-0.6b-dynamic-int4", 8L*gib, "23124RA7EO")
        assertNotNull(redmi)
        assertTrue(redmi!!.explanation.contains("3/3"))
        assertTrue(redmi.needsQualification)
        val other = MobileModelAdvice.forModel("qwen3-0.6b-dynamic-int4", 8L*gib, "different-phone")
        assertFalse(other!!.explanation.contains("3/3"))
        assertTrue(other.explanation.contains("stability probe"))
    }

    @Test fun guideDoesNotFabricateUnknownArtifactOrLargePhoneClaims() {
        assertNull(MobileModelAdvice.forModel("hf-unknown", 8L*gib, "23124RA7EO"))
        assertNull(MobileModelAdvice.forModel("qwen3-0.6b-dynamic-int4", 12L*gib, "23124RA7EO"))
        assertNull(MobileModelAdvice.forModel("qwen3-0.6b-dynamic-int4", 0, "23124RA7EO"))
    }

    @Test fun knownFailuresAreAdvisoryAndNotGpuQualification() {
        val answer = MobileModelAdvice.forModel("qwen3-1.7b-dynamic-int4", 8L*gib, "23124RA7EO")!!
        assertTrue(answer.explanation.contains("GPU fails"))
        assertTrue(answer.needsQualification)
        assertFalse(MobileModelAdvice.forModel("smollm2-360m-instruct", 8L*gib, "23124RA7EO")!!.explanation.contains("GPU passes"))
    }
}
