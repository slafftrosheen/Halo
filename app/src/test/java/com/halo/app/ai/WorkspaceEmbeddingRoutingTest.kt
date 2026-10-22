package com.halo.app.ai

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.NoteEntity
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.study.StudyRepository
import com.halo.app.data.workspace.WorkspaceFileEntity
import com.halo.providers.litert.models.LiteRtModelManager
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import com.halo.app.storage.haloStorageLayout
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class WorkspaceEmbeddingRoutingTest {
    @Test fun selectedEmbeddingExecutesIndexAndQueryWithOneOwnerAndExplicitSourceConsent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val storage = MemoryStorage()
        val requests = mutableListOf<EmbeddingRequest>()
        val constructed = mutableListOf<String>()
        val sha = "a".repeat(64)
        try {
            val noteId = "selected-study-note"
            db.notebookDao().insertNoteObject(
                WorkspaceObjectEntity(noteId, "NOTE", "Selected lesson notes", 1L, 1L,
                    "USER", "ACTIVE", false, "READY", "note:$noteId"),
                NoteEntity(noteId, "Selected source explains that fractions compare equal parts.", 1),
            )
            val textFileId = "selected-study-text-file"
            val textFile = java.io.File(haloStorageLayout(context).managedWorkspaceFiles, "$textFileId.txt")
            textFile.parentFile?.mkdirs()
            val textFilePrefix = "Selected text file explains equivalent fractions with equal parts."
            val textFilePadding = "x".repeat(65_400)
            textFile.writeText(textFilePrefix + textFilePadding + "A")
            db.notebookDao().insertWorkspaceObject(
                WorkspaceObjectEntity(textFileId, "FILE", "Fraction reference", 1L, 1L,
                    "USER", "ACTIVE", false, "READY", "file:$textFileId"),
            )
            db.workspaceFileDao().insertFile(
                WorkspaceFileEntity(textFileId, android.net.Uri.fromFile(textFile).toString(),
                    textFile.name, "text/plain", textFile.length(), 1L, textFile.absolutePath,
                    false, "HALO_OWNED", "OWNED_LOCAL"),
            )
            val source = StudyRepository(db, db.studyDao()).createSession(listOf(noteId, textFileId), 1L,
                StudyLanguagePreferences(), "Fractions compare equal parts")
            val service = WorkspaceSemanticService(context, db, storage, LiteRtModelManager(context),
                NativeResourceCoordinator(scope = scope), scope, embeddingFactory = { model ->
                    constructed += model
                    object : EmbeddingProvider {
                        override val id = "provider.litert.embedding"
                        override suspend fun embed(request: EmbeddingRequest): EmbeddingBatch {
                            requests += request
                            return EmbeddingBatch(EmbeddingSpace(request.modelSha256, 2, true,
                                "litertlm/0.18.0", request.preprocessingIdentity),
                                request.texts.map { listOf(1f, 0f) }, 1L)
                        }
                    }
                })
            WorkloadPreferences(storage).selectEmbedding("embedding-only", sha, 2)
            assertTrue(service.selectedContextExcerpts("fractions").isEmpty())
            assertTrue(requests.isEmpty())
            assertEquals(0,service.readyIndexSize())
            service.configure(true, setOf("study:${source.id}"))
            service.synchronize("embedding-only", sha, 2)
            assertTrue(service.readyIndexSize() > 0)
            // Search consent alone cannot inject private passages into a chat turn.
            assertTrue(service.selectedContextExcerpts("fractions").isEmpty())
            service.configureAssistantContext(true)
            val excerpts = service.selectedContextExcerpts("fractions").joinToString("\n")
            assertTrue(excerpts.contains("Fractions"))
            assertTrue(excerpts.contains("Selected source explains"))
            assertTrue(excerpts.contains("Selected text file explains"))
            assertEquals(listOf("embedding-only"), constructed)
            val initialEmbeddingRequestCount = requests.size
            assertTrue(initialEmbeddingRequestCount >= 2)
            assertTrue(requests.all { it.modelSha256 == sha && it.dimension == 2 })
            // The final byte falls beyond Study's bounded indexed text excerpt, but the full
            // bounded source hash must still invalidate the Study index when it changes.
            textFile.writeText(textFilePrefix + textFilePadding + "B")
            service.synchronize("embedding-only", sha, 2)
            val refreshedEmbeddingRequestCount = requests.size
            assertTrue(refreshedEmbeddingRequestCount > initialEmbeddingRequestCount)
            // A changed source selection is a new permission scope for assistant replies.
            service.configure(true, setOf("study:${source.id}", "note:another"))
            assertFalse(service.localSearchAccess().assistantContextEnabled)
            assertTrue(service.selectedContextExcerpts("fractions").isEmpty())
            service.configure(false, emptySet())
            assertEquals(0,service.readyIndexSize())
            assertTrue(service.selectedContextExcerpts("fractions").isEmpty())
            assertEquals(refreshedEmbeddingRequestCount, requests.size)
        } finally { scope.cancel(); scope.coroutineContext[Job]?.join(); db.close() }
    }

    private class MemoryStorage : Storage {
        private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
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
