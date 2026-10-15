package com.halo.app.ui.screens.home

import com.halo.runtime.cognition.agent.ActionTruth
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeToolResultFxTest {
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
