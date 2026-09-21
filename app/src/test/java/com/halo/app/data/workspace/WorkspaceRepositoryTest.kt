package com.halo.app.data.workspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.RoomNotebookRepository
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.notebook.WorkspaceObjectType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkspaceRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var notebookRepo: RoomNotebookRepository
    private lateinit var workspaceRepo: RoomWorkspaceRepository
    private lateinit var fileRepo: RoomWorkspaceFileRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        notebookRepo = RoomNotebookRepository(db.notebookDao())
        workspaceRepo = RoomWorkspaceRepository(db.notebookDao())
        fileRepo = RoomWorkspaceFileRepository(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createNoteAndImportFileAppearsInWorkspaceRepository() = runTest {
        val note = notebookRepo.createNote("Halo UI Blueprint")
        val file = fileRepo.importFile(
            uri = Uri.parse("content://com.example/document/1"),
            displayName = "protocol.pdf",
            mimeType = "application/pdf",
            sizeBytes = 1024L
        )

        val objects = workspaceRepo.observe(WorkspaceQuery()).first()
        assertEquals(2, objects.size)

        val noteObj = workspaceRepo.get(WorkspaceObjectId(note.id))
        assertNotNull(noteObj)
        assertEquals(WorkspaceObjectType.NOTE, noteObj?.type)
        assertEquals("Halo UI Blueprint", noteObj?.title)

        val fileObj = workspaceRepo.get(file.objectId)
        assertNotNull(fileObj)
        assertEquals(WorkspaceObjectType.FILE, fileObj?.type)
        assertEquals("protocol.pdf", fileObj?.title)
    }

    @Test
    fun archiveAndTrashChangeWorkspaceObjectLifecycle() = runTest {
        val note = notebookRepo.createNote("Lifecycle test")
        val id = WorkspaceObjectId(note.id)

        workspaceRepo.archive(id)
        val archivedList = workspaceRepo.observe(WorkspaceQuery(lifecycle = WorkspaceObjectLifecycle.ARCHIVED)).first()
        assertEquals(1, archivedList.size)
        assertEquals(id, archivedList.first().id)

        workspaceRepo.restore(id)
        val activeList = workspaceRepo.observe(WorkspaceQuery(lifecycle = WorkspaceObjectLifecycle.ACTIVE)).first()
        assertEquals(1, activeList.size)
        assertEquals(id, activeList.first().id)
    }

    @Test
    fun sendIntentHandlerImportsSharedText() = runTest {
        val handler = SendIntentHandler(notebookRepo, fileRepo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Shared from web")
            putExtra(Intent.EXTRA_SUBJECT, "Interesting Article")
        }

        val count = handler.handleIntent(intent)
        assertEquals(1, count)

        val notes = notebookRepo.observeNotes().first()
        assertEquals(1, notes.size)
        assertTrue(notes.first().body.contains("Shared from web"))
    }
}
