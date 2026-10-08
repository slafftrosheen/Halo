package com.halo.app.wear

import com.halo.runtime.cognition.interaction.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class WearPrivacyTest {
    @Test fun `nonnearby content is never transmitted`() = runTest {
        var transmissions = 0
        val transport = NearbyWearContentTransport({ false }, { _, _, _ -> transmissions++ })
        assertFalse(transport.sendSurface("remote", null)); assertEquals(0, transmissions)
    }
    @Test fun `surface projection contains only public bounded interaction fields`() {
        val surface = InteractionSurface(InteractionSurfaceId("surface"), null, null, null,
            InteractionSurfaceKind.CONFIRMATION, "Public title", "Public message",
            InteractionContent.Confirmation("Approve public operation"), emptyList(), InteractionPresentationPolicy(),
            Instant.now(), null, provenance = InteractionSurfaceProvenance(InteractionSurfaceSource.SYSTEM, "PRIVATE_INTERNAL_SOURCE"),
            supplements = listOf(InteractionSupplement.Notice("PRIVATE_SUPPLEMENT")))
        val bytes = WearSurfaceCodec.encode(surface)
        val wire = bytes.toString(Charsets.UTF_8)
        assertTrue(wire.contains("Approve public operation"))
        assertFalse(wire.contains("PRIVATE_")); assertFalse(wire.contains("provenance"))
        assertFalse(wire.contains("runId")); assertFalse(wire.contains("supplements"))
        assertTrue(bytes.size <= WearSurfaceCodec.MAX_BYTES)
    }
    @Test fun `watch submits existing typed response and unsupported types fail closed`() {
        val bytes = """{"version":1,"id":"surface","response":"CONFIRM"}""".toByteArray()
        assertEquals(InteractionResponse.Confirmed(InteractionSurfaceId("surface")), WearSurfaceCodec.response(bytes))
        assertTrue(runCatching { WearSurfaceCodec.response("""{"version":1,"id":"surface","response":"EXECUTE_TOOL"}""".toByteArray()) }.isFailure)
    }
}
