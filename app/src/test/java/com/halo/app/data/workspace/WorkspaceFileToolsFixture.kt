package com.halo.app.data.workspace

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.*
import kotlinx.coroutines.*
import org.robolectric.Shadows
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Shared isolated canonical owners. No live graph, provider, profiles, user files or OS changes. */
internal class WorkspaceFileToolsFixture : AutoCloseable {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val root = File(context.cacheDir, "file-tools-fixture-" + UUID.randomUUID()).also { it.mkdirs() }
    val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val documents = NotebookDocumentRepository(database.notebookDocumentDao(), database)
    val notes = RoomNotebookRepository(database.notebookDao(), documents = documents)
    val workspace = RoomWorkspaceRepository(database.notebookDao())
    val files = RoomWorkspaceFileRepository(context, database, root, ownedUri = { file ->
        val uri = Uri.parse("content://file-tools-fixture/${file.parentFile.name}/${file.name}")
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { file.inputStream() }
        uri
    })
    val versions = WorkspaceFileVersions(files, workspace)
    val text = WorkspaceTextDocuments(context, database, notes, documents, files, workspace)
    val receipts = WorkspaceFileReceipts(database, notes, documents)
    val media = WorkspaceMediaFiles(context, files, versions, receipts)
    val annotations = WorkspaceFileAnnotations(context, database, notes, documents, files, versions)
    val canvases = NotebookCanvasRepository(database, database.notebookCanvasDao(), database.notebookDao())
    val tools = WorkspaceFileTools(database, text, media, annotations, receipts, files, workspace, versions, documents, canvases,
        WorkspaceFileToolsRequests(scope, {}))
    suspend fun bytes(file: WorkspaceFile) = File(requireNotNull(database.workspaceFileDao().getFile(file.objectId.value)?.ownedCopyPath)).readBytes()
    suspend fun png(sourceId: String, name: String = "source.png"): WorkspaceFile {
        val bitmap = Bitmap.createBitmap(160, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val bytes = try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() } finally { bitmap.recycle() }
        val source = notes.createNote(title = sourceId)
        return files.createManagedFile(name, "image/png", bytes, source.id, source.revision.toString(), "{}")
    }
    fun reference(file: WorkspaceFile) = WorkspaceFileVersion(file.objectId.value, requireNotNull(file.version))
    override fun close() {
        scope.cancel(); database.close()
        check(root.canonicalFile.parentFile == context.cacheDir.canonicalFile)
        root.deleteRecursively()
    }
}
