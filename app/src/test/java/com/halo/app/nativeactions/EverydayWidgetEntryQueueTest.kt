package com.halo.app.nativeactions

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class EverydayWidgetEntryQueueTest {
    @Test fun aLaterShellDestinationRevokesAnOlderHeldWidgetEntryWithoutClearingTheNewDestination()=runTest {
        val saved=SavedStateHandle();val destinations=MutableStateFlow<String?>(null);val navigation=MutableStateFlow(0L)
        val held=CompletableDeferred<Unit>()
        val owner=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        val queue=EverydayWidgetEntryQueue(saved,owner,destinations,resolve={withContext(NonCancellable) {held.await()};"old-widget"},navigation=navigation)
        queue.offer(91,1,"open","reviewed");runCurrent()
        destinations.value="screen/sources";navigation.value=1;runCurrent()
        held.complete(Unit);runCurrent()
        assertEquals("screen/sources",destinations.value);assertNull(saved.get<Bundle>("widget.pendingEntry"));owner.cancel()
    }
    @Test fun pendingResolverSurvivesRestorationUntilTheResumedShellClaimsItsExactDestination() = runTest {
        val saved=SavedStateHandle();val destinations=MutableStateFlow<String?>(null)
        val firstScope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        val held=CompletableDeferred<Unit>()
        val first=EverydayWidgetEntryQueue(saved,firstScope,destinations,resolve={held.await();"first"})
        first.offer(71,4,"open","immutable-reviewed-target");runCurrent()
        firstScope.cancel()
        val restored=SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val secondScope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        var observed: WidgetEntryRequest?=null
        EverydayWidgetEntryQueue(restored,secondScope,destinations,resolve={observed=it;"screen/organizer?recordId=retained"})
        runCurrent()
        assertEquals(71,observed!!.id);assertEquals(4L,observed!!.version);assertEquals("immutable-reviewed-target",observed!!.payload)
        assertNotNull(restored.get<Bundle>("widget.pendingEntry"))
        destinations.value=null;runCurrent()
        assertNull(restored.get<Bundle>("widget.pendingEntry"));secondScope.cancel()
    }
    @Test fun aLateUncancellableResolverCannotReplaceTheNewerExplicitTap() = runTest {
        val saved=SavedStateHandle();val destinations=MutableStateFlow<String?>(null)
        val scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler));val release=CompletableDeferred<Unit>()
        val submitted=mutableListOf<String>()
        val queue=EverydayWidgetEntryQueue(saved,scope,destinations,resolve={ request ->
            if(request.id==81) withContext(NonCancellable) { release.await() }
            "destination:${request.id}"
        },submitted={submitted+=it})
        queue.offer(81,1,"open","old");runCurrent()
        queue.offer(82,1,"open","new");runCurrent();release.complete(Unit);runCurrent()
        assertEquals(listOf("destination:82"),submitted);assertEquals("destination:82",destinations.value)
        destinations.value=null;runCurrent();scope.cancel()
    }
}
