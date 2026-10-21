package com.halo.app.ui.screens.settings

import com.halo.runtime.cognition.routine.*
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineBrandBookFlowTest {
    @Test fun runAndConfirmationRequireReadySetupAndReviewedActions() {
        RoutineUiStatus.entries.forEach { status ->
            assertEquals(status in setOf(RoutineUiStatus.ACTIVE, RoutineUiStatus.PAUSED), routineCanRun(status, false))
            assertEquals(false, routineCanRun(status, true))
            assertEquals(true, routineCanToggle(true, status, true))
            assertEquals(routineCanRun(status, false), routineCanToggle(false, status, false))
        }
    }

    @Test fun technicalFailureDetailsNeverReachEverydayCopy() {
        assertEquals("Could not complete this change. Review the routine and try again.",
            routineErrorLabel("https://private.server token=secret tool_id=internal"))
        assertEquals("Choose a future time", routineErrorLabel("Choose a future time"))
        assertEquals(null, routineRunExplanation(RoutineRunState.COMPLETED))
    }

    @Test fun unknownDeliveryNeverLooksLikeAnActiveQueue() {
        assertEquals("Delivery not confirmed", deliveryResultLabel("NEW_TRANSPORT_STATE"))
        assertEquals("Delivery not confirmed", deliveryResultLabel("COMPLETION_UNKNOWN"))
        assertEquals("Delivery pending", deliveryResultLabel(null))
        assertEquals("Delivery queued", deliveryResultLabel("QUEUED"))
    }

    @Test fun pausedRoutineNeverPromisesAnEventOrStaleScheduledRun() {
        val now = Instant.parse("2026-10-21T12:00:00Z")
        val routine = Routine(RoutineId("paused"), "My routine", false,
            RoutineTrigger.NetworkAvailable, actions = emptyList(), createdAt = now,
            updatedAt = now, nextRunAt = now.plusSeconds(600))
        assertEquals("No runs while off", routine.nextRunLabel())
        assertEquals("No runs while off", routine.copy(nextRunAt = null).nextRunLabel())
    }
}
