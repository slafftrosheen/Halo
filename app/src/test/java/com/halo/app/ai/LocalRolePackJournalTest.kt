package com.halo.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRolePackJournalTest {
    @Test
    fun transactionJournalRoundTripsExactBaselineAndStage() {
        val journal = LocalRolePackJournal(
            phase = LocalRolePackPhase.INSTALLING,
            stage = LocalRolePackStage.SPEECH_OUTPUT,
            intentExisted = true,
            intentVerified = true,
            planFingerprint = "f".repeat(64),
            whisperSmallInstalled = false,
            vadInstalled = true,
            ttsFingerprints = setOf("b".repeat(64), "a".repeat(64)),
        )

        val restored = LocalRolePackJournal.decode(journal.encode())

        assertEquals(journal.copy(ttsFingerprints = journal.ttsFingerprints.toSortedSet()), restored)
    }

    @Test
    fun malformedOrUnknownJournalIsDiscarded() {
        assertNull(LocalRolePackJournal.decode("INSTALLING\tFUTURE_STAGE"))
        assertNull(LocalRolePackJournal.decode("not a transaction journal"))
    }

    @Test
    fun interruptedInstallCanResumeAndRollBackButCompletedPackCannot() {
        val interrupted = LocalRolePackInstallState(phase = LocalRolePackPhase.PAUSED)
        val complete = LocalRolePackInstallState(phase = LocalRolePackPhase.COMPLETE)

        assertTrue(interrupted.canResume)
        assertTrue(interrupted.canRollback)
        assertFalse(complete.canResume)
        assertFalse(complete.canRollback)
    }
}
