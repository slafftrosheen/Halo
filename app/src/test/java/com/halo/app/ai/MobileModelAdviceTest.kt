package com.halo.app.ai

import org.junit.Assert.*
import org.junit.Test

class MobileModelAdviceTest {
    private val gib = 1024L * 1024 * 1024

    @Test fun adviceIsDeviceScopedAndNeverActsAsProviderQualification() {
        val redmi = MobileModelAdvice.forModel("qwen3-0.6b-dynamic-int4", 8L*gib, "23124RA7EO", "03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856")
        assertNotNull(redmi)
        assertTrue(redmi!!.explanation.contains("3/3"))
        assertTrue(redmi.needsQualification)
        val other = MobileModelAdvice.forModel("qwen3-0.6b-dynamic-int4", 8L*gib, "different-phone")
        assertFalse(other!!.explanation.contains("3/3"))
        assertTrue(other.explanation.contains("stability probe"))
        val changedArtifact = MobileModelAdvice.forModel("qwen3-0.6b-dynamic-int4", 8L*gib, "23124RA7EO", "f".repeat(64))!!
        assertFalse(changedArtifact.explanation.contains("3/3"))
    }

    @Test fun guideDoesNotFabricateUnknownArtifactOrLargePhoneClaims() {
        assertNull(MobileModelAdvice.forModel("hf-unknown", 8L*gib, "23124RA7EO"))
        assertNull(MobileModelAdvice.forModel("qwen3-0.6b-dynamic-int4", 12L*gib, "23124RA7EO"))
        assertNull(MobileModelAdvice.forModel("qwen3-0.6b-dynamic-int4", 0, "23124RA7EO"))
    }

    @Test fun knownFailuresAreAdvisoryAndNotGpuQualification() {
        val answer = MobileModelAdvice.forModel("qwen3-1.7b-dynamic-int4", 8L*gib, "23124RA7EO", "2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b")!!
        assertTrue(answer.explanation.contains("GPU fails"))
        assertTrue(answer.needsQualification)
        assertFalse(MobileModelAdvice.forModel("smollm2-360m-instruct", 8L*gib, "23124RA7EO")!!.explanation.contains("GPU passes"))
    }
}
