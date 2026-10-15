package com.halo.providers.litert.runtime

import com.halo.sdk.provider.LocalComputeBackend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtControlProbeSummaryTest {
    private fun result(status: LiteRtControlProbeStatus) = LiteRtControlProbeResult(
        modelId = "fixture", artifactFingerprint = "fixture-sha",
        requestedBackend = LocalComputeBackend.CPU, effectiveBackend = LocalComputeBackend.CPU,
        requestedContextTokens = 1024, effectiveContextTokens = 1024,
        stages = listOf(LiteRtControlProbeStageResult(LiteRtControlProbeStage.FIRST_TOKEN, status, 35000)),
        startedAtMs = 0, totalDurationMs = 35001, controlProbePassed = status == LiteRtControlProbeStatus.PASSED,
    )

    @Test fun `no answer duration is not presented as measured first token latency`() {
        val line = result(LiteRtControlProbeStatus.FAILED).summaryLine()
        assertTrue(line.contains("answerWaitMs=35000"))
        assertFalse(line.contains("firstTokenMs="))
        assertTrue(line.contains("failedStage=FIRST_TOKEN"))
    }

    @Test fun `observed first token retains its measured latency`() {
        val line = result(LiteRtControlProbeStatus.PASSED).summaryLine()
        assertTrue(line.contains("firstTokenMs=35000"))
        assertFalse(line.contains("answerWaitMs="))
    }
}
