package com.halo.app.lab.bridge

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HaloLabEvidenceArtifactTest {
    private val attemptId = "123e4567-e89b-12d3-a456-426614174000"

    @Test fun opensOnlyExactTerminalFixedSmokeOutput() {
        assertArrayEquals(
            "READY".toByteArray(),
            HaloLabEvidenceArtifact.resolve(attemptId, "output-$attemptId", true,
                HaloLabProtocol.FAST_SMOKE_FIXTURE_ID, "READY", false),
        )
    }

    @Test fun rejectsModelArtifactIdsAndUnknownIds() {
        assertNull(HaloLabEvidenceArtifact.resolve(attemptId, "model-id", true,
            HaloLabProtocol.FAST_SMOKE_FIXTURE_ID, "READY", false))
        assertNull(HaloLabEvidenceArtifact.resolve(attemptId, "output-unknown", true,
            HaloLabProtocol.FAST_SMOKE_FIXTURE_ID, "READY", false))
    }

    @Test fun rejectsNonTerminalWrongFixtureAndTruncatedOutput() {
        assertNull(HaloLabEvidenceArtifact.resolve(attemptId, "output-$attemptId", false,
            HaloLabProtocol.FAST_SMOKE_FIXTURE_ID, "READY", false))
        assertNull(HaloLabEvidenceArtifact.resolve(attemptId, "output-$attemptId", true,
            "other-fixture", "READY", false))
        assertNull(HaloLabEvidenceArtifact.resolve(attemptId, "output-$attemptId", true,
            HaloLabProtocol.FAST_SMOKE_FIXTURE_ID, "READY", true))
    }

    @Test fun rejectsOversizedOrMalformedAttemptIdentity() {
        assertNull(HaloLabEvidenceArtifact.resolve(attemptId, "output-$attemptId", true,
            HaloLabProtocol.FAST_SMOKE_FIXTURE_ID, "x".repeat(513), false))
        assertNull(HaloLabEvidenceArtifact.resolve("not-a-uuid", "output-not-a-uuid", true,
            HaloLabProtocol.FAST_SMOKE_FIXTURE_ID, "READY", false))
    }
}
