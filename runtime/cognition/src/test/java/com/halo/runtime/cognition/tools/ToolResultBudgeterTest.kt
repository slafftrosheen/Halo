package com.halo.runtime.cognition.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolResultBudgeterTest {
    @Test
    fun largeLogsKeepInitialContextAndFinalError() {
        val original = (1..120).joinToString("\n") { "scan $it: " + "ok ".repeat(25) } +
            "\nFATAL: permission was revoked"
        val shortened = ToolResultBudgeter.budgetText(original, 480)
        assertTrue(shortened.length <= 480)
        assertTrue(shortened.startsWith("scan 1:"))
        assertTrue(shortened.endsWith("FATAL: permission was revoked"))
        assertTrue(shortened.contains("content incomplete"))
        val notice = Regex("\n\\.\\.\\. \\[tool result truncated: (\\d+) chars omitted; content incomplete] \\.\\.\\.\n")
            .find(shortened)!!
        val retained = shortened.length - notice.value.length
        assertEquals(original.length - retained, notice.groupValues[1].toInt())
    }

    @Test
    fun jsonOutputDoesNotMasqueradeAsCompleteAfterTruncation() {
        val source = """{"items":[""" + (1..100).joinToString(",") { "\"record-$it\"" } + """]}"""
        val bounded = ToolResultBudgeter.budgetText(source, 210)
        assertTrue(bounded.startsWith("{"))
        assertTrue(bounded.contains("content incomplete"))
        assertTrue(bounded.length <= 210)
        assertTrue(bounded.endsWith("]}"))
    }

    @Test
    fun allSmallBudgetsAreHardCaps() {
        val value = "a".repeat(600)
        for (limit in 0..90) {
            val bounded = ToolResultBudgeter.budgetText(value, limit)
            assertTrue("limit=$limit", bounded.length <= limit)
            if (limit > 0) assertTrue(bounded.isNotEmpty())
        }
    }

    @Test
    fun truncationNeverBreaksUtf16SurrogatePairs() {
        val original = "👓".repeat(200) + "\nEnd of report"
        val bounded = ToolResultBudgeter.budgetText(original, 199)
        assertTrue(bounded.endsWith("End of report"))
        for (index in bounded.indices) {
            if (Character.isHighSurrogate(bounded[index])) {
                assertTrue(index + 1 < bounded.length && Character.isLowSurrogate(bounded[index + 1]))
            }
            if (Character.isLowSurrogate(bounded[index])) {
                assertTrue(index > 0 && Character.isHighSurrogate(bounded[index - 1]))
            }
        }
    }

    @Test
    fun originalLengthsAndBytesRemainAvailableToDiagnostics() {
        val output = "€".repeat(150)
        val result = ToolExecutionResult.Success("call-1", "halo_read", output)
        val bounded = ToolResultBudgeter.budgetResult(result, 120) as ToolExecutionResult.Success
        assertTrue(bounded.output.length <= 120)
        assertTrue(bounded.outputMetadata!!.truncated)
        assertEquals(output.length, bounded.outputMetadata!!.originalChars)
        assertEquals(output.toByteArray(Charsets.UTF_8).size, bounded.outputMetadata!!.originalUtf8Bytes)
        assertFalse(result.outputMetadata != null)
    }

    @Test
    fun alreadyBoundedOutputAndFailuresKeepTheirMeaning() {
        val success = ToolExecutionResult.Success("call-2", "halo_read", "small result")
        val bounded = ToolResultBudgeter.budgetResult(success, 100) as ToolExecutionResult.Success
        assertEquals("small result", bounded.output)
        assertFalse(bounded.outputMetadata!!.truncated)
        val failed = ToolExecutionResult.Failed("call-3", "halo_read", "connection lost")
        assertSame(failed, ToolResultBudgeter.budgetResult(failed, 30))
    }
}
