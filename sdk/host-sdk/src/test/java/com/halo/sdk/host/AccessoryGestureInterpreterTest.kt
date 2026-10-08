package com.halo.sdk.host

import org.junit.Assert.*
import org.junit.Test

class AccessoryGestureInterpreterTest {
    private val interpreter = AccessoryGestureInterpreter().apply { connect("endpoint", "epoch") }
    private fun event(action: AccessoryControlAction, at: Long, classified: Boolean = false,
        evidence: ControlEvidence = ControlEvidence.VERIFIED_PLATFORM, epoch: String = "epoch") =
        AccessoryControlEvent.Button(AccessoryControl.PRIMARY, action, at,
            ControlDelivery("endpoint", epoch, "$action-$at", ControlOrigin.WEAR_FOREGROUND, evidence, classified, true))
    private fun accept(action: AccessoryControlAction, at: Long, double: Boolean = true) = interpreter.accept(event(action, at), at, double)
    @Test fun delayedSingleOccursOnce() {
        accept(AccessoryControlAction.PRESS, 100); assertTrue(accept(AccessoryControlAction.RELEASE, 180).isEmpty())
        assertTrue(interpreter.advance(529).isEmpty())
        assertEquals(AccessoryControlAction.SINGLE, interpreter.advance(530).single().action)
        assertTrue(interpreter.advance(900).isEmpty())
    }
    @Test fun doubleSuppressesSingle() {
        accept(AccessoryControlAction.PRESS, 100); accept(AccessoryControlAction.RELEASE, 180)
        accept(AccessoryControlAction.PRESS, 240)
        assertEquals(AccessoryControlAction.DOUBLE, accept(AccessoryControlAction.RELEASE, 300).single().action)
        assertTrue(interpreter.advance(1_000).isEmpty())
    }
    @Test fun longSuppressesTapAndReleaseReplay() {
        accept(AccessoryControlAction.PRESS, 100)
        assertEquals(AccessoryControlAction.LONG, accept(AccessoryControlAction.RELEASE, 750).single().action)
        assertTrue(accept(AccessoryControlAction.RELEASE, 750).isEmpty()); assertTrue(interpreter.advance(1_500).isEmpty())
    }
    @Test fun debounceRejectsBounceAndRepeatedDown() {
        accept(AccessoryControlAction.PRESS, 100); assertTrue(accept(AccessoryControlAction.RELEASE, 110).isEmpty())
        assertTrue(accept(AccessoryControlAction.PRESS, 120).isEmpty())
        accept(AccessoryControlAction.RELEASE, 180)
        assertEquals(1, interpreter.advance(600).size)
    }
    @Test fun vendorClassificationIsImmediateAndNeverTimedAgain() {
        val vendor = event(AccessoryControlAction.DOUBLE, 100, true, ControlEvidence.VERIFIED_VENDOR)
        assertEquals(listOf(vendor), interpreter.accept(vendor, 100, true)); assertTrue(interpreter.advance(1_000).isEmpty())
        assertTrue(interpreter.accept(vendor, 100, true).isEmpty())
    }
    @Test fun observedAndUnknownAreNotAssignable() {
        for (evidence in listOf(ControlEvidence.OBSERVED, ControlEvidence.UNKNOWN))
            assertTrue(interpreter.accept(event(AccessoryControlAction.SINGLE, 100, true, evidence), 100, true).isEmpty())
    }
    @Test fun noDoubleMappingDoesNotDelaySingle() {
        accept(AccessoryControlAction.PRESS, 100, false)
        assertEquals(AccessoryControlAction.SINGLE, accept(AccessoryControlAction.RELEASE, 180, false).single().action)
    }
    @Test fun disconnectCancelsPendingAndRejectsOldEpoch() {
        accept(AccessoryControlAction.PRESS, 100); accept(AccessoryControlAction.RELEASE, 180)
        interpreter.disconnect("endpoint"); interpreter.connect("endpoint", "new")
        assertTrue(interpreter.advance(1_000).isEmpty())
        assertTrue(accept(AccessoryControlAction.PRESS, 1_000).isEmpty())
    }
    @Test fun staleAndFutureEventsAreRejected() {
        assertTrue(interpreter.accept(event(AccessoryControlAction.SINGLE, 100, true), 3_000, false).isEmpty())
        assertTrue(interpreter.accept(event(AccessoryControlAction.SINGLE, 500, true), 100, false).isEmpty())
    }
    @Test fun unreliableRawEdgesNeverProduceGestures() {
        val raw = event(AccessoryControlAction.PRESS, 100).let { it.copy(delivery = it.delivery!!.copy(reliableEdges = false)) }
        assertTrue(interpreter.accept(raw, 100, true).isEmpty()); assertTrue(interpreter.advance(1_000).isEmpty())
    }
    @Test fun expiredFirstTapIsPreservedBeforeNewHold() {
        accept(AccessoryControlAction.PRESS, 100); accept(AccessoryControlAction.RELEASE, 180)
        assertEquals(AccessoryControlAction.SINGLE, accept(AccessoryControlAction.PRESS, 600).single().action)
        assertEquals(AccessoryControlAction.LONG, accept(AccessoryControlAction.RELEASE, 1_300).single().action)
    }
}
