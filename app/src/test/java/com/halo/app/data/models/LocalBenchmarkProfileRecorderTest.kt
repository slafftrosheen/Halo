package com.halo.app.data.models

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.LiteRtLocalProvider.ProviderIds
import com.halo.providers.litert.models.*
import com.halo.providers.litert.runtime.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalBenchmarkProfileRecorderTest {
    private val storage = object : Storage {
        override fun getString(key: String, defaultValue: String?) = defaultValue
        override fun getInt(key: String, defaultValue: Int) = defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean) = defaultValue
        override fun putString(key: String, value: String?) {}
        override fun putInt(key: String, value: Int) {}
        override fun putBoolean(key: String, value: Boolean) {}
        override fun remove(key: String) {}
        override fun clear() {}
    }
    private class Inventory(var descriptor: LocalModelDescriptor) : LocalModelInventoryRepository {
        override val inventoryState = MutableStateFlow<LocalModelInventoryState>(LocalModelInventoryState.Ready(listOf(descriptor), 1))
        override suspend fun refresh(reason: ModelInventoryRefreshReason) {}
        override suspend fun resolveModel(modelId: String) = descriptor.takeIf { it.id == modelId }
        override fun invalidate(reason: String) {}
    }
    private fun result(sha: String, text: String = "benchmark answer") = LocalModelBenchmarkResult(
        modelId = "artifact", displayName = "Artifact", artifactSha256 = sha, artifactBytes = 4,
        requestedBackend = LocalComputeBackend.AUTO, effectiveBackend = LocalComputeBackend.CPU,
        requestedContextTokens = 4096, effectiveContextTokens = 4096, loadDurationMs = 1200,
        engineInitDurationMs = 1200, conversationCreateDurationMs = 20, timeToFirstTokenMs = 3000,
        generationDurationMs = 4000, estimatedTokenCount = 34, estimatedTokensPerSecond = 8.4,
        responseChars = text.length, fallbackReason = null, contextFallbackReason = null,
        responseText = text, promptText = "private benchmark prompt", reasoningChars = 0, peakHeapBytes = 100,
    )

    @Test fun calibrationSurvivesFailureAndRejectsActualByteChangesEvenWithSameMetadata() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("runtime060", ".litertlm", context.cacheDir).apply { writeText("halo") }
        val sha = DeviceRuntimeIdentity.hash("halo")
        val inventory = Inventory(LocalModelDescriptor("artifact", "Artifact", file.absolutePath, 4,
            file.lastModified(), sha, 4096, false, false, LocalModelValidation.VERIFIED_ARTIFACT))
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = ModelRuntimeProfileRepository(db.modelRuntimeProfileDao())
            val provider = LiteRtLocalProvider(context, inventory, storage, nativeResourceCoordinator = null)
            val device = DeviceRuntimeIdentity("device", "coarse", "coarse", 34, 256)
            val source = RoomLocalInferenceProfileSource(inventory, repo, device, { provider })
            val recorder = LocalBenchmarkProfileRecorder(repo, source, device)
            assertNull(source.profile(ProviderIds.LITERT, "artifact"))
            recorder.record(result(sha).copy(effectiveContextTokens = null), sha)
            val unknown = db.modelRuntimeProfileDao().getProfilesForModel("artifact", "device").single()
            assertNull(unknown.contextSize)
            assertTrue(unknown.textGenerationProven)
            assertNull(source.profile(ProviderIds.LITERT, "artifact"))
            recorder.record(result(sha), sha)
            val profile = source.profile(ProviderIds.LITERT, "artifact")!!
            assertEquals(3000L, profile.ttftMs)
            assertEquals(8.4, profile.estimatedGenerationTokensPerSecond!!, 0.001)
            recorder.failure("artifact", sha, LocalModelBenchmarkFailure(BenchmarkFailureCode.GENERATION_FAILED,
                LocalComputeBackend.CPU, 4096, 4, 0, IllegalStateException("private output and stack")))
            assertEquals(3, db.modelRuntimeProfileDao().getProfilesForModel("artifact", "device").size)
            assertEquals(3000L, source.profile(ProviderIds.LITERT, "artifact")!!.ttftMs)
            val failure = db.modelRuntimeProfileDao().getProfilesForModel("artifact", "device").single { !it.isStable }
            assertEquals("GENERATION_FAILED", failure.failureReason)
            assertFalse(failure.toString().contains("private"))
            val modified = file.lastModified()
            file.writeText("beta"); file.setLastModified(modified)
            assertNull(source.profile(ProviderIds.LITERT, "artifact"))
            inventory.descriptor = inventory.descriptor.copy(sha256 = DeviceRuntimeIdentity.hash("beta"))
            assertNull(source.profile(ProviderIds.LITERT, "artifact"))
            recorder.record(result(sha), sha)
            assertEquals(3, db.modelRuntimeProfileDao().getProfilesForModel("artifact", "device").size)
        } finally { db.close(); file.delete() }
    }

    @Test fun coarseFingerprintIsDeterministicAndBounded() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = DeviceRuntimeIdentity.current(context)
        assertEquals(first, DeviceRuntimeIdentity.current(context))
        assertTrue(first.fingerprint.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(DeviceRuntimeIdentity.hash("hardware-a"), DeviceRuntimeIdentity.hash("hardware-b"))
    }
}
