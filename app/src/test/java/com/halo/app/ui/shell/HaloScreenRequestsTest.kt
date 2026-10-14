package com.halo.app.ui.shell

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class HaloScreenRequestsTest {
    @Test fun backgroundHostCannotStealTheCompactHostsRequest() {
        val pending = MutableStateFlow<String?>("mini_halo")
        assertNull(HaloScreenRequests.claim(pending,false))
        assertEquals("mini_halo",pending.value)
        assertEquals("mini_halo",HaloScreenRequests.claim(pending,true))
        assertNull(HaloScreenRequests.claim(pending,true))
    }
    @Test fun arbitraryRoutesAreDiscarded() {
        val pending = MutableStateFlow<String?>("notebook/private-id")
        assertNull(HaloScreenRequests.claim(pending,true)); assertNull(pending.value)
    }
}
