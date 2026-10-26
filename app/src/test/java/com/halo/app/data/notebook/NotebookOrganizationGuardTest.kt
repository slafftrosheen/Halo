package com.halo.app.data.notebook

import android.app.Application
import com.halo.app.data.workspace.WorkspaceFileToolsFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real isolated Room tables and existing repository; no production/user data or provider. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotebookOrganizationGuardTest {
    @Test fun favoriteRejectsChangedSourceAndChangedFavoriteAndRevokedAdmission() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val repo = NotebookOrganizationRepository(f.database.notebookOrganizationDao())
            val note = f.notes.createNote(title = "Guard fixture")
            val initial = repo.captureGuarded(note.id, null)
            f.workspace.updateTitle(WorkspaceObjectId(note.id), "Concurrent owner edit")
            assertNull(repo.applyGuarded(initial, NotebookOrganizationChange.Favorite(true)) { true })
            assertFalse(f.database.notebookOrganizationDao().isFavorite(note.id))
            val next = repo.captureGuarded(note.id, null)
            repo.setFavorite(note.id, true)
            assertNull(repo.applyGuarded(next, NotebookOrganizationChange.Favorite(false)) { true })
            val current = repo.captureGuarded(note.id, null)
            assertNull(repo.applyGuarded(current, NotebookOrganizationChange.Favorite(false)) { false })
            assertTrue(f.database.notebookOrganizationDao().isFavorite(note.id))
            assertNotNull(repo.applyGuarded(current, NotebookOrganizationChange.Favorite(false)) { true })
            assertFalse(f.database.notebookOrganizationDao().isFavorite(note.id))
        }
    }

    @Test fun collectionsUseExistingTablesReadBackEffectsAndPreserveSourceOnDelete() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val repo = NotebookOrganizationRepository(f.database.notebookOrganizationDao())
            val note = f.notes.createNote(title = "Retained source")
            val create = requireNotNull(repo.applyGuarded(repo.captureGuarded(null, null),
                NotebookOrganizationChange.CreateCollection("Owned collection")) { true })
            val id = requireNotNull(create.collectionId)
            val member = requireNotNull(repo.applyGuarded(repo.captureGuarded(note.id, id),
                NotebookOrganizationChange.Membership(true)) { true })
            assertEquals(true, member.value)
            val staleDelete = repo.captureGuarded(null, id)
            repo.setMembership(id, note.id, false)
            assertNull(repo.applyGuarded(staleDelete, NotebookOrganizationChange.DeleteCollection) { true })
            assertNotNull(repo.applyGuarded(repo.captureGuarded(null, id), NotebookOrganizationChange.DeleteCollection) { true })
            assertTrue(repo.captureGuarded(null, null).collections.isEmpty())
            assertNotNull(f.workspace.get(WorkspaceObjectId(note.id)))
        }
    }

    @Test fun missingTargetsAndTrashedObjectsCannotBeOrganized() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val repo = NotebookOrganizationRepository(f.database.notebookOrganizationDao())
            assertNull(repo.applyGuarded(repo.captureGuarded("missing", null), NotebookOrganizationChange.Favorite(true)) { true })
            val note = f.notes.createNote(title = "Trashed fixture")
            f.workspace.trash(WorkspaceObjectId(note.id))
            assertNull(repo.applyGuarded(repo.captureGuarded(note.id, null), NotebookOrganizationChange.Favorite(true)) { true })
            assertNull(repo.applyGuarded(repo.captureGuarded(null, "missing"), NotebookOrganizationChange.DeleteCollection) { true })
        }
    }
}
