package com.halo.providers.litert.runtime

import org.junit.Assert.*
import org.junit.Test

class HaloToolMarkupStreamGuardTest {
    @Test fun ordinaryToolFreeAnswerStillStreamsWithoutDroppingCharacters() {
        val guard = HaloToolMarkupStreamGuard()
        val input = "Hello! I can help explain the difference between search and phone actions."
        val chunks = input.chunked(3)
        val out = chunks.joinToString("") { guard.offer(it) } + guard.finish()
        assertEquals(input, out)
        assertFalse(guard.rejectedMarkup)
    }

    @Test fun splitOpenMarkerIsNeverEmittedToSpeech() {
        val guard = HaloToolMarkupStreamGuard()
        val streamed = StringBuilder()
        listOf("Sure, I will ", "<ha", "lo_tool_", "call>", "{\"name\":\"halo_volume\"}", "</halo_tool_call>")
            .forEach { streamed.append(guard.offer(it)) }
        streamed.append(guard.finish())
        assertTrue(guard.rejectedMarkup)
        assertFalse(streamed.toString().contains("halo_tool_call"))
        assertFalse(streamed.toString().contains("<ha"))
    }

    @Test fun closingMarkerWithoutOpenMarkerIsAlsoBlocked() {
        val guard = HaloToolMarkupStreamGuard()
        listOf("Text ", "</halo_", "tool_call>", " ignored").forEach(guard::offer)
        assertTrue(guard.rejectedMarkup)
        assertEquals("", guard.finish())
    }
}
