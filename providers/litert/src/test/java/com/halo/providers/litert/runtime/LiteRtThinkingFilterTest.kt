package com.halo.providers.litert.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtThinkingFilterTest {

    @Test
    fun `think block is suppressed from visible output but retained as reasoning`() {
        val filter = LiteRtThinkingFilter()
        val visible = filter.accept("Hello <think>secret plan</think>world") + filter.finish()

        assertEquals("Hello world", visible)
        assertEquals("secret plan", filter.reasoningText())
        assertTrue(filter.rawText().contains("<think>"))
        assertTrue(filter.hasReasoning)
    }

    @Test
    fun `a tag split across chunks is buffered instead of leaked`() {
        val filter = LiteRtThinkingFilter()
        val visible = StringBuilder()
        listOf("A <thi", "nk>hidden</thi", "nk>B").forEach { chunk ->
            visible.append(filter.accept(chunk))
        }
        visible.append(filter.finish())

        assertEquals("A B", visible.toString())
        assertEquals("hidden", filter.reasoningText())
    }

    @Test
    fun `plain text passes through untouched`() {
        val filter = LiteRtThinkingFilter()
        val visible = filter.accept("Just a normal answer.") + filter.finish()
        assertEquals("Just a normal answer.", visible)
        assertFalse(filter.hasReasoning)
    }

    @Test
    fun `an unterminated think block is withheld from visible output`() {
        val filter = LiteRtThinkingFilter()
        val visible = filter.accept("<think>only reasoning") + filter.finish()

        assertEquals("", visible)
        assertEquals("only reasoning", filter.reasoningText())
    }

    @Test
    fun `thinking variant tags are handled too`() {
        val filter = LiteRtThinkingFilter()
        val visible = filter.accept("<thinking>inner</thinking>outer") + filter.finish()

        assertEquals("outer", visible)
        assertEquals("inner", filter.reasoningText())
    }

    @Test
    fun `split helper separates visible text and reasoning`() {
        val (visible, reasoning) = LiteRtThinkingFilter.split("<think>x</think>answer")
        assertEquals("answer", visible)
        assertEquals("x", reasoning)
    }

    @Test
    fun `a lone angle bracket is not swallowed`() {
        val filter = LiteRtThinkingFilter()
        val visible = filter.accept("1 < 2") + filter.finish()
        assertEquals("1 < 2", visible)
    }
}
