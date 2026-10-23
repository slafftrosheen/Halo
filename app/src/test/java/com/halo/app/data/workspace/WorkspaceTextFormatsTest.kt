package com.halo.app.data.workspace

import org.junit.Assert.*
import org.junit.Test

class WorkspaceTextFormatsTest {
    @Test fun csvQuotedMultilineEscapedQuotesAndTrailingCellsRoundTripWithoutEvaluation() {
        val input = "Name,Value,Note\r\n\"A,B\",\"a\"\"b\",\"line1\nline2\"\r\n=1+2,,"
        val table = WorkspaceCsv.parse(input)
        assertEquals(listOf("A,B", "a\"b", "line1\nline2"), table.rows[1])
        assertEquals(listOf("=1+2", "", ""), table.rows[2])
        assertEquals(table, WorkspaceCsv.parse(table.encode()))
        assertEquals(listOf(listOf("")), WorkspaceCsv.parse("").rows)
        assertEquals(listOf(listOf("a", "")), WorkspaceCsv.parse("a,\r\n").rows)
        assertEquals(listOf(listOf("a"), listOf("")), WorkspaceCsv.parse("a\n\n").rows)
    }
    @Test fun csvEditsPreserveOtherCellsAndRejectUnsupportedStructure() {
        val original = WorkspaceCsv.parse("a,b\r\nc")
        val changed = original.set(1, 1, "comma,value").insertRow(1).insertColumn(1)
        assertEquals("a", changed.rows[0][0])
        assertEquals("comma,value", changed.rows[2][2])
        assertEquals(original.set(1, 1, "comma,value"), changed.deleteRow(1).deleteColumn(1))
        assertEquals(listOf("c"), original.rows[1])
        assertTrue(runCatching { original.set(0, 64, "x") }.isFailure)
        assertTrue(runCatching { original.set(0, 0, "x".repeat(16001)) }.isFailure)
        assertTrue(runCatching { WorkspaceCsv.parse("one").deleteRow(0) }.isFailure)
        assertTrue(runCatching { WorkspaceCsv.parse("one").deleteColumn(0) }.isFailure)
    }
    @Test fun malformedQuotesAndRowColumnBudgetsFailExplicitly() {
        listOf("\"unclosed", "unquoted\"quote", "\"closed\"tail").forEach { assertTrue(it, runCatching { WorkspaceCsv.parse(it) }.isFailure) }
        assertTrue(runCatching { WorkspaceCsv.parse(List(1001) { "row" }.joinToString("\n")) }.isFailure)
        assertTrue(runCatching { WorkspaceCsv.parse(List(65) { "cell" }.joinToString(",")) }.isFailure)
        assertTrue(runCatching { WorkspaceCsv(List(200) { List(64) { "" } }).requireValid() }.isFailure)
    }
    @Test fun utf8KeepsLiteralTextAndRejectsBinaryInvalidEncodingAndUnpairedSurrogates() {
        val text = "\n# Sveiki · Привет\n```csv\na,b\n```\n\n"
        assertEquals(text, WorkspaceTextEncoding.decode(WorkspaceTextEncoding.encode(text)))
        assertEquals("Sveiki", WorkspaceTextEncoding.decode(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + "Sveiki".toByteArray()))
        assertEquals("", WorkspaceTextEncoding.decode(byteArrayOf()))
        assertTrue(runCatching { WorkspaceTextEncoding.decode(byteArrayOf(0xc3.toByte(), 0x28)) }.isFailure)
        assertTrue(runCatching { WorkspaceTextEncoding.decode(byteArrayOf(0)) }.isFailure)
        assertTrue(runCatching { WorkspaceTextEncoding.encode("\uD800") }.isFailure)
        assertTrue(runCatching { WorkspaceTextEncoding.encode("x".repeat(200001)) }.isFailure)
    }
    @Test fun formatDetectionNeverTreatsOfficeOrExecutableBinaryAsText() {
        assertEquals(WorkspaceTextFormat.CSV, WorkspaceTextFormat.fromFile("table.csv", "text/plain"))
        assertEquals(WorkspaceTextFormat.MARKDOWN, WorkspaceTextFormat.fromFile("notes.MD", "application/octet-stream"))
        assertEquals(WorkspaceTextFormat.TXT, WorkspaceTextFormat.fromFile("notes.txt", "text/plain"))
        assertNull(WorkspaceTextFormat.fromFile("book.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertNull(WorkspaceTextFormat.fromFile("script.js", "application/javascript"))
        assertNull(WorkspaceTextFormat.fromLanguage("csv"))
    }
}
