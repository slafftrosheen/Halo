package com.halo.app.lab.bridge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabAttemptStageBudgetsTest {
    @Test fun acceptsOnlyBoundedStageBudgetsAndSumsExactly() {
        val budgets = LabAttemptStageBudgets(30_000L, 120_000L, 15_000L)
        assertTrue(budgets.valid())
        assertEquals(165_000L, budgets.totalMs)
        assertEquals(budgets, LabAttemptStageBudgets.parse(budgets.toJson()))
    }

    @Test fun rejectsInvalidOrOverflowedStageBudgets() {
        assertNull(LabAttemptStageBudgets.parse(JSONObject()
            .put("loadMs", 0L).put("executeMs", 30_000L).put("unloadMs", 10_000L)))
        assertTrue(!LabAttemptStageBudgets(Long.MAX_VALUE, 1L, 1L).valid())
    }
}
