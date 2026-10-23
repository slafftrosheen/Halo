package com.halo.app.nativeactions

import com.halo.sdk.provider.*
import org.junit.Assert.*
import org.junit.Test

class NativeRequestSharingTest {
    @Test fun nativeActivityHandoffRetainsExactSharingOriginAndCannotReplayAfterCancellation() {
        val origin = RemoteDispatchProvenance(setOf(RemoteDataCategory.WORKSPACE), 47)
        val (id, _) = NativeActivityRequests.create(origin)
        try {
            assertTrue(NativeActivityRequests.active(id))
            assertEquals(origin, NativeActivityRequests.remoteProvenance(id))
            NativeActivityRequests.cancel(id)
            assertFalse(NativeActivityRequests.active(id))
            assertNull(NativeActivityRequests.remoteProvenance(id))
        } finally { NativeActivityRequests.cancel(id) }
    }
}
