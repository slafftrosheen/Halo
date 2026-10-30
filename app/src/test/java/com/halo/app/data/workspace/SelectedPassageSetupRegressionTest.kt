package com.halo.app.data.workspace

import androidx.lifecycle.ViewModelStore
import com.halo.app.data.study.StudyJson
import com.halo.app.data.study.StudyRepository
import com.halo.app.data.notebook.DocumentSelection
import com.halo.app.notebook.NotebookUseCases
import com.halo.app.study.*
import com.halo.app.ui.screens.notebook.NoteEditorViewModel
import com.halo.app.ui.screens.study.StudyHomeViewModel
import com.halo.sdk.runtime.study.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SelectedPassageSetupRegressionTest {
    @Test fun setupPreservesSelectionPreviewSavedAnchorAndLearnerChoices() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val owners = ViewModelStore()
        try {
            WorkspaceFileToolsFixture().use { f ->
                val note = f.notes.createNote("Copper leaves rest beside a quiet lake.")
                val doc = requireNotNull(f.documents.loadLegacyDocument(note.id))
                val anchor = StudyTextAnchor(doc.blocks.first().id, 0, 6)
                val editor = NoteEditorViewModel(note.id, NotebookUseCases(f.notes), document = f.documents)
                owners.put("editor", editor)
                withTimeout(5000) { editor.uiState.first { it.blocks.isNotEmpty() } }
                editor.setSelection(DocumentSelection(anchor.blockId, 6, 0))
                val ref = requireNotNull(editor.studySetupSource())
                assertEquals(anchor, ref.textAnchor)
                // This is the exact persisted navigation payload, not a copied passage/goal.
                val restored = StudyJson.decodeSources(StudyJson.encodeSources(listOf(ref)), emptyList(), null).single()
                val resolver = WorkspaceStudySourceResolver(f.context, f.documents, f.workspace, f.files, noPdf())
                val repository = StudyRepository(f.database, f.database.studyDao())
                val study = StudyUseCases(repository, documents = f.documents, sourceResolver = resolver)
                val home = StudyHomeViewModel(study, resolver = resolver)
                owners.put("home", home)
                home.select(note.id, sourceReference = restored)
                val preview = withTimeout(5000) { home.previews.first { it.isNotEmpty() } }.single() as ResolvedStudySource.Text
                assertEquals("Copper", preview.passages.single().text)
                home.start("Exact passage", "lv", strongest = "en", level = StudyLearnerLevel.LOWER_SECONDARY,
                    style = StudySupportStyle.PRACTICE, executionChoice = "phone")
                val id = withTimeout(5000) { home.navigation.first() }
                val saved = requireNotNull(repository.getSession(id))
                assertEquals(restored, saved.sources.single())
                assertEquals("Copper", study.sourceReferences(saved).single().excerpt)
                assertEquals(StudyLearnerLevel.LOWER_SECONDARY, saved.learnerLevel)
                assertEquals(StudySupportStyle.PRACTICE, saved.supportStyle)
                assertEquals("lv", saved.languages.explanationLanguage)
                assertEquals("en", saved.languages.strongestLanguage)
            }
        } finally { owners.clear(); Dispatchers.resetMain() }
    }

    @Test fun sourceEditDuringSetupRejectsInsteadOfBroadeningOrCreatingALesson() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val note = f.notes.createNote("Copper leaves rest beside a quiet lake.")
            val doc = requireNotNull(f.documents.loadLegacyDocument(note.id))
            val ref = StudySessionSource(note.id, doc.revision.toString(), StudyTextAnchor(doc.blocks.first().id, 0, 6))
            val repository = StudyRepository(f.database, f.database.studyDao())
            val study = StudyUseCases(repository, documents = f.documents,
                sourceResolver = WorkspaceStudySourceResolver(f.context, f.documents, f.workspace, f.files, noPdf()))
            assertTrue(f.notes.saveNote(note.id, "Unrelated replacement text"))
            val result = study.startMaterial(listOf(note.id), StudyLanguagePreferences(), "A goal",
                policy = StudyKnowledgePolicy.SOURCE_PLUS_GENERAL, sourceReferences = mapOf(note.id to ref))
            assertEquals(StudyUnavailableReason.SOURCE_CHANGED, (result as StudyStartResult.Unavailable).reason)
            assertTrue(repository.observeActiveSessions().first().isEmpty())
        }
    }
}
