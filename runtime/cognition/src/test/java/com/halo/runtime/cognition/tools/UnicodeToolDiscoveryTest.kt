package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import org.junit.Assert.*
import org.junit.Test

class UnicodeToolDiscoveryTest {
    private fun service(vararg tools: ToolDescriptor): ToolDiscoveryService {
        val executor = object : ToolExecutor {
            override fun definitions() = tools.map { it.definition }
            override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = error("Discovery must not execute")
        }
        return ToolDiscoveryService(ToolRegistry(listOf(executor), tools.toList()))
    }
    private fun tool(id: String, words: Set<String>, domain: String) = ToolDescriptor(
        ToolId(id), ToolDefinition(id, id, "Read canonical data"), ToolCategory.WORKSPACE,
        keywords = words, discoveryDomain = domain)
    @Test fun fullyCyrillicAndAccentedQueriesFindRealActions() {
        val notes = tool("halo_note_get", setOf("заметки", "piezīmes", "notatki"), "Notes")
        val sources = tool("halo_source_query", setOf("источники", "źródła"), "Sources")
        val discovery = service(notes, sources)
        for (query in listOf("ЗАМЕТКИ", "piezīmes", "notatki")) assertEquals(listOf(notes), discovery.search(query))
        assertEquals(listOf(sources), discovery.search("źródła"))
        assertEquals(listOf(notes), discovery.search("halo_note_get"))
    }
    @Test fun largeAlphabeticalDomainCannotHideLaterDomains() {
        val crowded = (0..60).map { tool("aaa_$it", setOf("read"), "Crowded") }
        val organizer = tool("zzz_organizer", setOf("plan"), "Organizer")
        val library = tool("zzz_library", setOf("reference"), "Reference libraries")
        val summary = service(*(crowded + organizer + library).toTypedArray()).catalogueSummary()
        assertTrue(summary.contains(organizer.name)); assertTrue(summary.contains(library.name))
        assertTrue(summary.length <= 3200)
        assertFalse(summary.contains("aaa_60\n"))
    }
}
