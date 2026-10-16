package com.halo.app.ui.screens.home

import com.halo.runtime.cognition.agent.ActionTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeToolResultFxTest {
    @Test fun historicReceiptsCannotReplayWhenHomeReopensOrConversationChanges() {
        val old = HomeToolResultReceipt("run:call:CONFIRMED", "halo_timer",
            HomeToolReceiptKind.CONFIRMED, 1000L)
        assertFalse(homeReceiptIsNew(old, 1001L))
        assertTrue(homeReceiptIsNew(old, 999L))
        assertFalse(homeReceiptIsNew(null, 100L))
    }

    @Test fun doneOnlyForConfirmedExecutionTruth() {
        assertEquals(HomeToolReceiptKind.CONFIRMED,
            homeToolReceiptKind(true, ActionTruth.CONFIRMED))
        for (truth in listOf(ActionTruth.DISPATCHED, ActionTruth.ACKNOWLEDGED,
            ActionTruth.UNKNOWN, ActionTruth.AUTHORIZED, ActionTruth.PROPOSED)) {
            assertEquals(HomeToolReceiptKind.UNVERIFIED, homeToolReceiptKind(true, truth))
        }
    }

    @Test fun failureNeverPlaysDoneEvenForContradictoryReceipt() {
        assertEquals(HomeToolReceiptKind.FAILED,
            homeToolReceiptKind(false, ActionTruth.CONFIRMED))
        assertEquals(HomeToolReceiptKind.FAILED,
            homeToolReceiptKind(true, ActionTruth.FAILED))
    }
}
