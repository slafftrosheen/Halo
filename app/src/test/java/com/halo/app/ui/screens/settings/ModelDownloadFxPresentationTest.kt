package com.halo.app.ui.screens.settings

import com.halo.providers.litert.models.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelDownloadFxPresentationTest {
    @Test fun fractionUsesObservedBytesWhenTotalKnown() {
        assertEquals(0.5f,
            modelDownloadFraction(DownloadState.Downloading(512, 1024, null))!!, 0.001f)
        assertEquals(1f,
            modelDownloadFraction(DownloadState.Verifying(1024, 1024))!!, 0.001f)
    }

    @Test fun unknownTotalMustNotPretendZeroProgress() {
        assertNull(modelDownloadFraction(DownloadState.Downloading(512, null, null)))
        assertNull(modelDownloadFraction(DownloadState.Queued))
        assertNull(modelDownloadFraction(DownloadState.Installed))
    }

    @Test fun reportedProgressIsBounded() {
        assertEquals(1f,
            modelDownloadFraction(DownloadState.Downloading(150, 100, 1.5f))!!, 0.001f)
        assertEquals(0f,
            modelDownloadFraction(DownloadState.Downloading(0, 100, -1f))!!, 0.001f)
    }
}
