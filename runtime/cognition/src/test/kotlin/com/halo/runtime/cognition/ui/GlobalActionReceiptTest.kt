package com.halo.runtime.cognition.ui

import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.sdk.runtime.ScreenContextSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalActionReceiptTest {
    private val before = ScreenContextSnapshot("app.one", null, null, null, emptyList(), 0, snapshotId = "before", uiRevision = 1)
    @Test fun unrelatedScreenChangeDoesNotConfirmLockScreenshotOrMedia() {
        val after = before.copy(packageName = "app.two", snapshotId = "after")
        for (operation in listOf(UiOperation.LOCK_SCREEN, UiOperation.TAKE_SCREENSHOT, UiOperation.MEDIA_PLAY_PAUSE, UiOperation.NOTIFICATIONS)) {
            val receipt = UiPostconditionVerifier().verify("action", UiActionIntent.Global("before", 1, operation), before, after,
                UiDispatchStatus.DISPATCHED, UiSettleStatus.SETTLED, 200)
            assertEquals(ActionTruth.DISPATCHED, receipt.actionTruth)
        }
    }
    @Test fun homeRequiresKnownLauncherAndSupportsAlreadyAtHome() {
        val intent = UiActionIntent.Global("before", 1, UiOperation.HOME)
        val after = before.copy(snapshotId = "after")
        val verifier = UiPostconditionVerifier()
        assertEquals(ActionTruth.DISPATCHED, verifier.verify("action", intent, before, after, UiDispatchStatus.DISPATCHED,
            UiSettleStatus.SETTLED, 200).actionTruth)
        assertEquals(ActionTruth.CONFIRMED, verifier.verify("action", intent, before, after, UiDispatchStatus.DISPATCHED,
            UiSettleStatus.SETTLED, 200, launcherPackage = "app.one").actionTruth)
    }
}
