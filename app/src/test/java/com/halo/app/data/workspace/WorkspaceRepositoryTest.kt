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
import java.io.File
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import org.robolectric.Shadows.shadowOf
import kotlinx.coroutines.CancellationException

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
        fileRepo = RoomWorkspaceFileRepository(context, db, File(context.filesDir, "owned"))
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
        val handler = SendIntentHandler(notebookRepo) { uri ->
            fileRepo.keepLocalCopyBounded(fileRepo.importFile(uri).objectId, 64L * 1024 * 1024)
        }
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

    @Test fun multipleSharesKeepSuccessfulItemsAndDeduplicateClipData() = runTest {
        val ok = Uri.parse("content://source/ok")
        val denied = Uri.parse("content://source/denied")
        val calls = mutableListOf<Uri>()
        val handler = SendIntentHandler(notebookRepo) { uri ->
            calls += uri
            if (uri == denied) throw SecurityException("Permission revoked")
            fileRepo.importFile(uri, displayName = "source.pdf", mimeType = "application/pdf")
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(ok, denied))
            clipData = android.content.ClipData.newRawUri("source", ok)
            putExtra(Intent.EXTRA_TEXT, "Source explanation")
        }
        val result = handler.importToInbox(intent)
        assertEquals(2, result.objectIds.size)
        assertEquals(1, result.failed)
        assertEquals(listOf(ok, denied), calls)
        assertTrue(result.objectIds.all { workspaceRepo.get(WorkspaceObjectId(it))?.inInbox == true })
    }

    @Test fun shareCancellationDoesNotBecomeSuccessOrContinueImporting() = runTest {
        var calls = 0
        val handler = SendIntentHandler(notebookRepo) { _ -> calls++; throw CancellationException() }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(Uri.parse("content://source/1"), Uri.parse("content://source/2")))
        }
        assertTrue(runCatching { handler.importToInbox(intent) }.exceptionOrNull() is CancellationException)
        assertEquals(1, calls)
    }

    @Test fun boundedCopyRejectsInterruptedEmptyAndOversizedReadsWithoutOwningPartialBytes() = runTest {
        val storage = File(context.filesDir, "workspace/owned/test-${java.util.UUID.randomUUID()}")
        val repository = RoomWorkspaceFileRepository(context, db, storage, ownedUri = Uri::fromFile)
        for ((name, stream) in listOf(
            "partial" to object : InputStream() { var count = 0; override fun read(): Int = if (count++ < 32) 1 else throw IOException("Interrupted") },
            "empty" to ByteArrayInputStream(byteArrayOf()),
            "large" to ByteArrayInputStream(ByteArray(200)),
        )) {
            val uri = Uri.parse("content://source/$name")
            shadowOf(context.contentResolver).registerInputStream(uri, stream)
            val imported = repository.importFile(uri, displayName = "$name.bin", mimeType = "application/octet-stream")
            assertTrue(runCatching { repository.keepLocalCopyBounded(imported.objectId, 100) }.isFailure)
            assertEquals(ContentOwnership.EXTERNAL_REFERENCE, repository.getFile(imported.objectId)?.ownership)
            assertTrue(storage.walkTopDown().none { it.isFile })
        }
    }

    @Test fun repeatedOwnedCopyKeepsIdentityAndBytesAndRevokedPermissionDoesNotCreateArtifact() = runTest {
        val storage = File(context.filesDir, "workspace/owned/test-${java.util.UUID.randomUUID()}")
        // AndroidX FileProvider uses '/' path boundaries; JVM Windows paths use '\\'.
        // Keep its production adapter and exercise real bounded copying with a fixture URI adapter.
        val repository = RoomWorkspaceFileRepository(context, db, storage, ownedUri = Uri::fromFile)
        val uri = Uri.parse("content://source/complete")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(ByteArray(80) { it.toByte() }))
        val imported = repository.importFile(uri, displayName = "complete.bin", mimeType = "application/octet-stream")
        val owned = repository.keepLocalCopyBounded(imported.objectId, 100)
        assertEquals(owned, repository.keepLocalCopyBounded(imported.objectId, 100))
        assertEquals(1, storage.walkTopDown().count { it.isFile })
        val badUri = Uri.parse("content://source/revoked")
        shadowOf(context.contentResolver).registerInputStream(badUri, object : InputStream() { override fun read(): Int = throw SecurityException("Revoked") })
        val bad = repository.importFile(badUri, displayName = "revoked.bin", mimeType = "application/octet-stream")
        assertTrue(runCatching { repository.keepLocalCopyBounded(bad.objectId, 100) }.isFailure)
        assertEquals(1, storage.walkTopDown().count { it.isFile })
    }
}
