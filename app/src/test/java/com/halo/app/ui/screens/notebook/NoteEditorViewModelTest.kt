package com.halo.app.ui.screens.notebook

import com.halo.app.data.notebook.NoteDraft
import com.halo.app.data.notebook.NotebookNote
import com.halo.app.data.notebook.NotebookRepository
import com.halo.app.data.notebook.WorkspaceLifecycle
import com.halo.app.data.notebook.WorkspaceSource
import com.halo.app.notebook.NotebookUseCases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NoteEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun newerDraftIsRecoveredAndThenCommitted() = runTest(dispatcher) {
        val repository = FakeNotebookRepository(
            note = testNote(body = "Committed", updatedAt = 100),
            draft = NoteDraft("note-1", "Recovered draft", updatedAt = 101)
        )
        val viewModel = NoteEditorViewModel(
            noteId = "note-1",
            useCases = NotebookUseCases(repository),
            autosaveDelayMs = 400
        )

        runCurrent()
        assertEquals("Recovered draft", viewModel.uiState.value.body)
        assertEquals(SaveStatus.SAVING, viewModel.uiState.value.status)
        assertEquals("Committed", repository.note?.body)

        advanceTimeBy(400)
        runCurrent()
        assertEquals("Recovered draft", repository.note?.body)
        assertNull(repository.draft)
        assertEquals(SaveStatus.SAVED, viewModel.uiState.value.status)
    }

    @Test
    fun editWritesRecoveryDraftBeforeDebouncedCommit() = runTest(dispatcher) {
        val repository = FakeNotebookRepository(note = testNote(body = "Original", updatedAt = 100))
        val viewModel = NoteEditorViewModel(
            noteId = "note-1",
            useCases = NotebookUseCases(repository),
            autosaveDelayMs = 400
        )
        runCurrent()

        viewModel.updateBody("Latest text")
        runCurrent()

        assertEquals("Latest text", repository.draft?.body)
        assertEquals("Original", repository.note?.body)
        assertEquals(SaveStatus.SAVING, viewModel.uiState.value.status)

        advanceUntilIdle()
        assertEquals("Latest text", repository.note?.body)
        assertNull(repository.draft)
        assertEquals(SaveStatus.SAVED, viewModel.uiState.value.status)
    }

    private fun testNote(body: String, updatedAt: Long) = NotebookNote(
        id = "note-1",
        title = body,
        body = body,
        createdAt = 1,
        updatedAt = updatedAt,
        source = WorkspaceSource.QUICK_NOTE,
        lifecycle = WorkspaceLifecycle.ACTIVE,
        inInbox = true,
        revision = 1
    )

    private class FakeNotebookRepository(
        var note: NotebookNote?,
        var draft: NoteDraft? = null
    ) : NotebookRepository {
        override fun observeNotes(
            query: String,
            lifecycle: WorkspaceLifecycle,
            inboxOnly: Boolean
        ): Flow<List<NotebookNote>> = flowOf(listOfNotNull(note))

        override suspend fun getNote(id: String): NotebookNote? = note

        override suspend fun getDraft(id: String): NoteDraft? = draft

        override suspend fun createNote(body: String, source: WorkspaceSource): NotebookNote =
            error("Not used")

        override suspend fun saveDraft(id: String, body: String) {
            draft = NoteDraft(id, body, (note?.updatedAt ?: 0) + 1)
        }

        override suspend fun saveNote(id: String, body: String): Boolean {
            val current = note ?: return false
            note = current.copy(body = body, title = body, updatedAt = current.updatedAt + 2)
            draft = null
            return true
        }

        override suspend fun setLifecycle(id: String, lifecycle: WorkspaceLifecycle): Boolean {
            val current = note ?: return false
            note = current.copy(lifecycle = lifecycle)
            return true
        }

        override suspend fun removeFromInbox(id: String): Boolean = true

        override suspend fun permanentlyDelete(id: String): Boolean {
            note = null
            draft = null
            return true
        }
    }
}
