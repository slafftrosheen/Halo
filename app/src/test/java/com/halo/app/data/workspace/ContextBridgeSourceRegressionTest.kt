package com.halo.app.data.workspace

import com.halo.app.ai.PRIVACY_MODE_KEY
import com.halo.app.data.study.StudyRepository
import com.halo.app.notebook.NotebookUseCases
import com.halo.app.study.*
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import com.halo.sdk.runtime.study.StudyTextAnchor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Source only in the requested coding batch. Execution is explicitly deferred. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ContextBridgeSourceRegressionTest {
    @Test fun selectedPassageSurvivesSessionReadbackAndCannotBroadenAfterSourceEdit() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val note = f.notes.createNote("Outside before\n\nSelected exact words\n\nOutside after")
            val doc = requireNotNull(f.documents.loadLegacyDocument(note.id))
            val block = doc.blocks.first { it.text == "Selected exact words" }
            val anchor = StudyTextAnchor(block.id, 0, "Selected exact".length)
            val repository = StudyRepository(f.database, f.database.studyDao())
            val useCases = StudyUseCases(repository, documents = f.documents,
                sourceResolver = WorkspaceStudySourceResolver(f.context, f.documents, f.workspace, f.files, noPdf()))
            val started = useCases.startNoteSelection(note.id, doc.revision, anchor) as StudyStartResult.Started
            val restored = requireNotNull(repository.getSession(started.session.id))
            assertEquals(anchor, restored.sources.single().textAnchor)
            assertEquals("Selected exact", useCases.sourceReferences(restored).single().excerpt)
            assertTrue(f.notes.saveNote(note.id, "Changed source with unrelated material"))
            assertFalse(useCases.sourcesStillCurrent(restored, useCases.sourceReferences(restored)))
            assertTrue(useCases.restartWithCurrentSources(restored.id) is StudyStartResult.Unavailable)
            useCases.continueWithCurrentSources(restored.id)
            assertEquals(anchor, repository.getSession(restored.id)!!.sources.single().textAnchor)
            assertNull(useCases.sourceReferences(repository.getSession(restored.id)!!).single().excerpt)
        }
    }

    @Test fun summaryRetryAfterOwnerRecreationReusesReceiptAndProtectsNotebookEdits() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val repository = StudyRepository(f.database, f.database.studyDao())
            val session = repository.createSession(emptyList(), null, StudyLanguagePreferences(), "A topic")
            val notes = NotebookUseCases(f.notes)
            val firstOwner = StudyUseCases(repository, documents = f.documents)
            val first = requireNotNull(firstOwner.saveSummaryArtifact(session.id, "First summary", "Notes", notes, null, null))
            // No local journey link is supplied: the existing Room provenance is authoritative.
            val restoredOwner = StudyUseCases(StudyRepository(f.database, f.database.studyDao()), documents = f.documents)
            val second = requireNotNull(restoredOwner.saveSummaryArtifact(session.id, "Updated summary", "Notes", notes, null, null))
            assertEquals(first.noteId, second.noteId)
            assertEquals(second, restoredOwner.summaryArtifact(session.id))
            assertTrue(f.notes.saveNote(second.noteId, "User edits must survive"))
            assertNull(restoredOwner.saveSummaryArtifact(session.id, "Do not overwrite", "Notes", notes, null, null))
            assertEquals("User edits must survive", f.notes.getNote(second.noteId)!!.body)
        }
    }

    @Test fun savedCloudModeCannotOverrideExplicitObjectLocalOnlyOrExclusion() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val note = f.notes.createNote("Child work")
            val storage = MemoryStorage()
            storage.putString(PRIVACY_MODE_KEY, "CLOUD")
            val policy = WorkspaceMediaAiPolicy(f.database, storage)
            assertTrue(policy.permitsObject(note.id, false)) // Unset default follows the profile.
            policy.setAccess(note.id, WorkspaceAiAccess.LOCAL_ONLY)
            assertFalse(policy.permitsObject(note.id, false))
            assertTrue(policy.permitsObject(note.id, true))
            policy.setAccess(note.id, WorkspaceAiAccess.EXCLUDE_FROM_AI)
            assertFalse(policy.permitsObject(note.id, true))
            assertFalse(policy.permitsObject(note.id, false))
            val context = com.halo.app.data.WorkspaceContextStore(f.database, f.notes, storage)
            assertNotNull(context.versionFor(note.id)) // Manual navigation/metadata stays available.
            context.select(note.id)
            assertNull(context.contentFor(note.id))
            assertNull(context.snapshot(note.id, context.versionFor(note.id)))
            assertTrue(context.entries(false).isEmpty())
            policy.setAccess(note.id, WorkspaceAiAccess.ALLOW_CONFIGURED_CLOUD)
            storage.putString(PRIVACY_MODE_KEY, "PRIVATE")
            assertFalse(policy.permitsObject(note.id, false))
        }
    }

    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
