package com.halo.app.ui.everyday

import com.halo.app.data.knowledge.*
import com.halo.app.everyday.collectors.*
import org.junit.Assert.*
import org.junit.Test

class ReferenceLibraryProjectionTest {
    private val source = CollectorSource("library:one", "Owned", "Imported", CollectorKind.LOCAL_LIBRARY,
        SourceCategory.CUSTOM, "en", selected = true, childApproved = true, version = 7)
    private val entry = ReferenceEntryEntity(sourceId = source.id, generation = "g", ordinal = 1, entryId = "water",
        title = "Water", text = "Owned passage", language = "en", url = null, publisher = "Owned", revision = null,
        license = "CC0", updatedAt = null, format = "TEXT")
    private val library = ReferenceLibraryEntity(source.id, "g", "Owned", "en", "fp", 1, 1)
    private val state = ReferenceLibrariesState(loading = false, kids = false,
        libraries = listOf(ReferenceLibraryRow(source, library)), entry = entry, entrySourceVersion = source.version,
        results = ReferenceSearchPage(listOf(entry), mapOf(source.id to source), "snapshot", null))
    private fun hidden(value: ReferenceLibrariesState, ready: Boolean = true, kids: Boolean = false) {
        assertNull(referenceVisibleEntry(value, ready, kids))
        assertTrue(referenceVisibleHits(value, ready, kids).isEmpty())
    }
    @Test fun matchingVisibleContentSurvivesAndFamilyTransitionsHideLateCompletedReads() {
        assertEquals(entry, referenceVisibleEntry(state, true, false))
        assertEquals(listOf(entry), referenceVisibleHits(state, true, false))
        hidden(state, ready = false); hidden(state, kids = true)
        val child = state.copy(kids = true)
        assertEquals(entry, referenceVisibleEntry(child, true, true))
        assertEquals(listOf(entry), referenceVisibleHits(child, true, true))
        hidden(child.copy(libraries = listOf(ReferenceLibraryRow(source.copy(childApproved = false), library))), kids = true)
    }
    @Test fun changedVersionGenerationSelectionOrRemovedSourceCannotReviveOldPassages() {
        hidden(state.copy(libraries = emptyList()))
        for (changed in listOf(source.copy(version = 8), source.copy(selected = false), source.copy(enabled = false), source.copy(muted = true)))
            hidden(state.copy(libraries = listOf(ReferenceLibraryRow(changed, library))))
        hidden(state.copy(libraries = listOf(ReferenceLibraryRow(source, library.copy(generation = "new")))))
    }
}
