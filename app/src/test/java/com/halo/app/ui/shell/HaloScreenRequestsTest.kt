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

    @Test fun anObservedRequestCannotConsumeItsNewerReplacement() {
        val pending = MutableStateFlow<String?>("voice_settings")
        val observed = pending.value
        pending.value = "notebook"
        assertNull(HaloScreenRequests.claim(pending, true, observed))
        assertEquals("notebook", pending.value)
        assertEquals("notebook", HaloScreenRequests.claim(pending, true, "notebook"))
    }

    @Test fun anObservedNullCannotConsumeANewRequest() {
        val pending = MutableStateFlow<String?>(null)
        val observed = pending.value
        pending.value = "voice_settings"
        assertNull(HaloScreenRequests.claim(pending, true, observed))
        assertEquals("voice_settings", pending.value)
    }
}
