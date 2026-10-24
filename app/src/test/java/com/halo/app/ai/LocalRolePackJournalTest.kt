package com.halo.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRolePackJournalTest {
    @Test fun legacyReceiptNeverInventsOwnershipAndNewReceiptRetainsExactToken() {
        val legacy = "PAUSED\tINTENT\tfalse\tfalse\t${"f".repeat(64)}\tfalse\tfalse\t\t"
        val restored = requireNotNull(LocalRolePackJournal.decode(legacy))
        assertNull(restored.transactionId)
        val token = "b88fe002-14e0-48dc-85bd-6dbcd038f709"
        assertEquals(token, LocalRolePackJournal.decode(restored.copy(transactionId = token).encode())?.transactionId)
        assertNull(LocalRolePackJournal.decode(legacy + "\t../unknown"))
        assertNull(LocalRolePackJournal.decode(legacy + "\t" + token + "\textra"))
    }
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
        assertNull(LocalRolePackJournal.decode("\t".repeat(16001)))
        assertNull(LocalRolePackJournal.decode(""))
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
