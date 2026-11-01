package com.halo.providers.litert.models

import org.junit.Assert.*
import org.junit.Test

class ModelQueueCancellationTest {
    @Test fun pauseBeforeRegistrationLeavesAResumableStateWithoutWaitingForWorkInfo() {
        assertEquals(DownloadState.Paused(0, 100), cancelledQueuePresentation(DownloadState.Queued, false, false, 0, 100))
        assertEquals(DownloadState.Paused(25, 100), cancelledQueuePresentation(DownloadState.Preparing, false, false, 25, 100))
    }
    @Test fun scopedPauseCannotOverwriteANewerResumeOrIndependentRegisteredWork() {
        assertNull(cancelledQueuePresentation(DownloadState.Queued, false, true, 0, 100))
        assertNull(cancelledQueuePresentation(DownloadState.Downloading(25,100,.25f), true, false, 25,100))
    }
    @Test fun olderQueuePauseCannotOverwriteALaterForegroundVerificationReceipt() {
        // The owner marks a still-running foreground job as a successor even without WorkInfo.
        assertNull(cancelledQueuePresentation(DownloadState.Preparing, false, true, 100,100))
    }
    @Test fun cancellationDoesNotEraseACompletedInstallOrPolicyRecoveryReason() {
        assertNull(cancelledQueuePresentation(DownloadState.Installed, false, false, 0,100))
        assertNull(cancelledQueuePresentation(DownloadState.Failed(ModelDownloadFailure.MODE_BLOCKED,"Power needed"), false,false,0,100))
    }
}
