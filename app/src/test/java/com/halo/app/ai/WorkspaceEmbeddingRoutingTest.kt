package com.halo.app.ai

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.study.StudyRepository
import com.halo.providers.litert.models.LiteRtModelManager
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.StudyLanguagePreferences
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
            val source = StudyRepository(db, db.studyDao()).createSession(emptyList(), null,
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
            service.configure(true, setOf("study:${source.id}"))
            service.synchronize("embedding-only", sha, 2)
            // Search consent alone cannot inject private passages into a chat turn.
            assertTrue(service.selectedContextExcerpts("fractions").isEmpty())
            service.configureAssistantContext(true)
            assertTrue(service.selectedContextExcerpts("fractions").single().contains("Fractions"))
            assertEquals(listOf("embedding-only"), constructed)
            assertEquals(2, requests.size)
            assertTrue(requests.all { it.modelSha256 == sha && it.dimension == 2 })
            // A changed source selection is a new permission scope for assistant replies.
            service.configure(true, setOf("study:${source.id}", "note:another"))
            assertFalse(service.localSearchAccess().assistantContextEnabled)
            assertTrue(service.selectedContextExcerpts("fractions").isEmpty())
            service.configure(false, emptySet())
            assertTrue(service.selectedContextExcerpts("fractions").isEmpty())
            assertEquals(2, requests.size)
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
