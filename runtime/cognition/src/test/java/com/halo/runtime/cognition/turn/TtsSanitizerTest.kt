package com.halo.runtime.cognition.turn

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsSanitizerTest {

    @Test
    fun `test sanitize normal answer`() {
        assertEquals("Hello world", TtsSanitizer.sanitize("Hello world"))
    }

    @Test
    fun `test sanitize think block`() {
        val input = "<think>\nReasoning here\n</think>\nFinal answer"
        assertEquals("Final answer", TtsSanitizer.sanitize(input))
    }

    @Test
    fun `test sanitize reasoning only`() {
        val input = "<think>\nOnly reasoning\n</think>"
        assertEquals("", TtsSanitizer.sanitize(input))
    }

    @Test
    fun `test sanitize json block`() {
        val input = "Here is the tool call: ```json\n{\"tool\": \"halo_probe\"}\n```"
        assertEquals("Here is the tool call:", TtsSanitizer.sanitize(input))
    }

    @Test
    fun `test sanitize markdown formatting`() {
        val input = "Here is **bold** and `code`"
        assertEquals("Here is bold and code", TtsSanitizer.sanitize(input))
    }

    @Test
    fun `test raw json block`() {
        val input = "{\n\"tool\": \"halo_probe\"\n}"
        assertEquals("", TtsSanitizer.sanitize(input))
    }
}
