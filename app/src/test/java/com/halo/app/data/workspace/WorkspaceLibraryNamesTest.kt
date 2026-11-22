package com.halo.app.data.workspace

import android.app.Application
import com.halo.app.data.notebook.LibraryCollectionSuggestion
import com.halo.app.data.notebook.LibrarySuggestedSource
import com.halo.app.data.notebook.NotebookOrganizationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkspaceLibraryNamesTest {
    @Test fun acceptedRenameKeepsFileAndHeaderAlignedAndExpiresTheOldName() = runBlocking {
        WorkspaceFileToolsFixture().use { fixture ->
            val original = fixture.png("Reference", "IMG_2041.png")
            val expected = requireNotNull(fixture.workspace.get(original.objectId))
            var now = 100L
            val names = WorkspaceLibraryNames(fixture.database) { now }
            assertFalse(names.rename(expected, "Old Town.jpg", original.version) { true })
            assertFalse(names.rename(expected, "Old Town.png", "stale-file-version") { true })
            assertFalse(names.rename(expected, "Old Town.png", original.version) { false })
            assertTrue(names.rename(expected, "Old Town.png", original.version) { true })
            assertFalse(names.rename(expected, "Another name.png", original.version) { true })
            assertEquals("Old Town.png", fixture.workspace.get(original.objectId)?.title)
            val file = fixture.files.getFile(original.objectId)
            assertEquals("Old Town.png", file?.displayName)
            assertEquals(original.version, file?.version)
            assertEquals("IMG_2041.png", names.observe().first()[original.objectId.value]?.oldTitle)
            now += 30L * 24 * 60 * 60 * 1000
            assertTrue(names.observe().first().isEmpty())
        }
    }

    @Test fun suggestionRechecksEverySourceAndUndoLeavesAReplacementMembership() = runBlocking {
        WorkspaceFileToolsFixture().use { fixture ->
            val organizer = NotebookOrganizationRepository(fixture.database.notebookOrganizationDao(), now = { 500L })
            val first = fixture.notes.createNote(title = "Riga packing")
            val second = fixture.png("Photo source", "Riga.jpg.png")
            val collection = requireNotNull(organizer.createCollection("Trip to Riga"))
            val snapshot = requireNotNull(fixture.workspace.get(second.objectId))
            val sources = listOf(LibrarySuggestedSource(first.id, first.title, first.updatedAt),
                LibrarySuggestedSource(snapshot.id.value, snapshot.title, snapshot.updatedAt))
            val suggestion = LibraryCollectionSuggestion(collection.id, collection.name, sources)
            assertNull(organizer.applyLibrarySuggestion(suggestion.copy(sources = sources.map { it.copy(updatedAt = -1) })) { true })
            assertTrue(organizer.observeMembers(collection.id).first().isEmpty())
            val kept = organizer.applyLibrarySuggestion(suggestion) { true }
            assertNotNull(kept)
            assertEquals(sources.map { it.id }.toSet(), organizer.observeMembers(collection.id).first())
            organizer.setMembership(collection.id, first.id, true)
            assertEquals(1, organizer.undoLibrarySuggestion(requireNotNull(kept)) { true })
            assertEquals(setOf(first.id), organizer.observeMembers(collection.id).first())
        }
    }
}
