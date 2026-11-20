package com.halo.drivers.heycyan.wifi

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FreshHeyCyanMediaInventoryTest {
    private fun count(photos: Int, type: Int = 4) = HeyCyanEvent.MediaCountsUpdated(photos, 0, 0, photos, 0, type)

    @Test fun subscribesBeforeRequestAndRequiresANewReplyEachTime() = runTest {
        val events = MutableSharedFlow<HeyCyanEvent>()
        var photos = 0
        var requests = 0
        val reader = FreshHeyCyanMediaInventory(events, {
            requests++
            events.emit(count(photos))
        }, { it == "chosen" })
        assertEquals(0, reader.read("chosen")?.photos)
        photos = 1
        assertEquals(1, reader.read("chosen")?.photos)
        assertEquals(2, requests)
    }

    @Test fun missingReplyIsUnknownAndNeverReusesAnOldZero() = runTest {
        val events = MutableSharedFlow<HeyCyanEvent>()
        events.emit(count(0))
        var requested = false
        val reader = FreshHeyCyanMediaInventory(events, { requested = true }, { true })
        assertNull(reader.read("chosen", 500))
        assertTrue(requested)
        assertEquals(500, testScheduler.currentTime)
    }

    @Test fun unrelatedAndInvalidRepliesCannotAuthorizeEmpty() = runTest {
        val events = MutableSharedFlow<HeyCyanEvent>()
        val reader = FreshHeyCyanMediaInventory(events, {
            events.emit(count(0, 1)); events.emit(count(-1)); events.emit(count(2))
        }, { true })
        assertEquals(2, reader.read("chosen")?.photos)
    }

    @Test fun selectionChangeRejectsEvenAFreshZero() = runTest {
        val events = MutableSharedFlow<HeyCyanEvent>()
        var current = true
        val reader = FreshHeyCyanMediaInventory(events, { current = false; events.emit(count(0)) }, { current })
        assertTrue(runCatching { reader.read("chosen") }.exceptionOrNull() is IllegalStateException)
    }

    @Test fun parentCancellationIsNotAnEmptyOrUnknownResult() = runTest {
        val events = MutableSharedFlow<HeyCyanEvent>()
        var returned = false
        val reader = FreshHeyCyanMediaInventory(events, { }, { true })
        val job = launch { reader.read("chosen"); returned = true }
        runCurrent(); job.cancelAndJoin()
        assertFalse(returned)
    }
}
