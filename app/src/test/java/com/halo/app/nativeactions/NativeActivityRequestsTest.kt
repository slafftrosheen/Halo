package com.halo.app.nativeactions

import org.junit.Assert.*
import org.junit.Test

class NativeActivityRequestsTest {
    @Test fun `capacity cannot invalidate another users active picker`() {
        NativeActivityRequests.cancelAll()
        try {
            val requests = (1..32).map { NativeActivityRequests.create() }
            assertThrows(IllegalStateException::class.java) { NativeActivityRequests.create() }
            assertTrue(requests.all { NativeActivityRequests.active(it.first) && it.second.isActive })
            NativeActivityRequests.cancel(requests.first().first)
            val replacement = NativeActivityRequests.create()
            assertTrue(NativeActivityRequests.active(replacement.first))
            assertTrue(requests.drop(1).all { NativeActivityRequests.active(it.first) })
        } finally { NativeActivityRequests.cancelAll() }
    }

    @Test fun `receipt eviction preserves all later completed results`() {
        NativeActivityRequests.cancelAll()
        val ids = (1..65).map {
            val (id, result) = NativeActivityRequests.create()
            NativeActivityRequests.complete(id, NativeActivityRequests.Receipt(true, "saved-$it"))
            assertTrue(result.isCompleted)
            id
        }
        assertNull(NativeActivityRequests.receipt(ids.first()))
        ids.drop(1).forEach { assertNotNull(NativeActivityRequests.receipt(it)) }
    }

    @Test fun `stop followed by late callback never confirms completion`() {
        val (id, result) = NativeActivityRequests.create()
        NativeActivityRequests.cancelAll()
        NativeActivityRequests.complete(id, NativeActivityRequests.Receipt(true, "late"))
        assertTrue(result.isCancelled)
        assertNull(NativeActivityRequests.receipt(id))
    }
}
