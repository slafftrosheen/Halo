package com.halo.app.di.tools

import org.junit.Assert.*
import org.junit.Test

class HostDiscoveryVocabularyTest {
    @Test fun clocksWorkspaceLibraryAndReferenceLibrariesHaveDistinctDomains() {
        assertEquals("World clock", HostDiscoveryVocabulary.domain("halo_clock_query"))
        assertEquals("Workspace Library", HostDiscoveryVocabulary.domain("halo_library_view"))
        assertEquals("Reference libraries", HostDiscoveryVocabulary.domain("halo_library_query"))
        assertTrue(HostDiscoveryVocabulary.keywords("halo_clock_query").contains("часы"))
    }
}
