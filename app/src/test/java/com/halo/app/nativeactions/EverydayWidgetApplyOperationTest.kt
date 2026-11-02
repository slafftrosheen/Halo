package com.halo.app.nativeactions

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EverydayWidgetApplyOperationTest {
    @Test fun rotationWhileCommitIsHeldRetainsOneOperationAndResumesCompletionBeforeReenablingCancel()=runTest {
        var receipt=false;var writes=0;var paints=0
        val commit=CompletableDeferred<Unit>();val paint=CompletableDeferred<Unit>()
        val session=WidgetApplySession(this,{receipt},{paints++;paint.await()})
        session.run {writes++;commit.await();receipt=true}
        runCurrent();assertTrue(session.state.value.busy)
        // Recreated activity binds the same ViewModel/session while its old IO is suspended.
        session.run();session.run {writes++}
        runCurrent();assertEquals(1,writes);assertTrue(session.state.value.busy)
        commit.complete(Unit);runCurrent();assertTrue(session.state.value.busy);assertEquals(1,paints)
        paint.complete(Unit);runCurrent();assertTrue(session.state.value.completed);assertFalse(session.state.value.busy)
        session.run {writes++};runCurrent();assertEquals(1,writes)
    }
    @Test fun lateDurableReceiptAfterAnInitialRecoveryReadCompletesInsteadOfDeadlockingOnOwnCasConflict()=runTest {
        var receipt=false;var paints=0;var writes=0
        val restored=WidgetApplySession(this,{receipt},{paints++})
        restored.run();runCurrent();assertFalse(restored.state.value.busy)
        restored.run {writes++;receipt=true;error("Another completion won the CAS")}
        runCurrent();assertTrue(restored.state.value.completed);assertFalse(restored.state.value.busy)
        assertEquals(1,writes);assertEquals(1,paints)
    }
    @Test fun snapshotInvalidationDuringPaintSettlesTheRetainedOwnerAfterVerifiedCommit()=runTest {
        var receipt=false
        val held=CompletableDeferred<Unit>()
        val session=WidgetApplySession(this,{receipt},{held.await();throw CancellationException("Family snapshot invalidated")})
        session.run {receipt=true};runCurrent();assertTrue(session.state.value.busy)
        held.complete(Unit);runCurrent()
        assertTrue(session.state.value.completed);assertFalse(session.state.value.busy);assertFalse(session.state.value.error)
    }
    @Test fun failedWriteWithoutVerifiedReceiptReportsRecoverableErrorAndDoesNotCompletePlacement()=runTest {
        var paints=0
        val session=WidgetApplySession(this,{false},{paints++})
        session.run {throw java.io.IOException("Synthetic disk failure")};runCurrent()
        assertFalse(session.state.value.completed);assertFalse(session.state.value.busy);assertTrue(session.state.value.error);assertEquals(0,paints)
    }
}
