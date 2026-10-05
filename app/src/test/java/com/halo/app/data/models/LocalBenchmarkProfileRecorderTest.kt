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
    private val stored = mutableMapOf<String, Any>()
    private val storage = object : Storage {
        override fun getString(key: String, defaultValue: String?) = stored[key] as? String ?: defaultValue
        override fun getInt(key: String, defaultValue: Int) = stored[key] as? Int ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean) = defaultValue
        override fun putString(key: String, value: String?) { if (value == null) stored.remove(key) else stored[key] = value }
        override fun putInt(key: String, value: Int) { stored[key] = value }
        override fun putBoolean(key: String, value: Boolean) {}
        override fun remove(key: String) { stored.remove(key) }
        override fun clear() {}
    }
    private class Inventory(var descriptor: LocalModelDescriptor) : LocalModelInventoryRepository {
        override val inventoryState = MutableStateFlow<LocalModelInventoryState>(LocalModelInventoryState.Ready(listOf(descriptor), 1))
        var verifications = 0
        var resolutions = 0
        var refreshes = 0
        override suspend fun refresh(reason: ModelInventoryRefreshReason) { refreshes++ }
        override suspend fun verifyPhysicalArtifact(modelId: String): LocalModelDescriptor? {
            verifications++
            val sha = DeviceRuntimeIdentity.hash(File(descriptor.path).readText())
            descriptor = descriptor.copy(sha256 = sha)
            inventoryState.value = LocalModelInventoryState.Ready(listOf(descriptor), verifications.toLong())
            return descriptor
        }
        override suspend fun resolveModel(modelId: String): LocalModelDescriptor? { resolutions++; return descriptor.takeIf { it.id == modelId } }
        override fun invalidate(reason: String) {}
    }
    private class CountingDao(private val delegate: ModelRuntimeProfileDao) : ModelRuntimeProfileDao by delegate {
        var lookups = 0
        override suspend fun getBestProfile(artifactId: String, artifactFingerprint: String, deviceFingerprint: String,
            runtimeVersion: String, compatibilityEpoch: Int, runtime: String, backend: String?, contextSize: Int?,
            threads: Int?, gpuLayers: Int?): ModelRuntimeProfileEntity? {
            lookups++
            return delegate.getBestProfile(artifactId, artifactFingerprint, deviceFingerprint, runtimeVersion,
                compatibilityEpoch, runtime, backend, contextSize, threads, gpuLayers)
        }
    }

    private fun result(sha: String, text: String = "benchmark answer") = LocalModelBenchmarkResult(
        modelId = "artifact", displayName = "Artifact", artifactSha256 = sha, artifactBytes = 1_200_000,
        requestedBackend = LocalComputeBackend.AUTO, effectiveBackend = LocalComputeBackend.CPU,
        requestedContextTokens = 4096, effectiveContextTokens = 4096, loadDurationMs = 1200,
        engineInitDurationMs = 1200, conversationCreateDurationMs = 20, timeToFirstTokenMs = 3000,
        generationDurationMs = 4000, estimatedTokenCount = 34, estimatedTokensPerSecond = 8.4,
        responseChars = text.length, fallbackReason = null, contextFallbackReason = null,
        responseText = text, promptText = "private benchmark prompt", reasoningChars = 0, peakHeapBytes = 100,
    )

    @Test fun calibrationSurvivesFailureAndRejectsActualByteChangesEvenWithSameMetadata() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val payload = "halo".repeat(300_000)
        val file = File.createTempFile("runtime061", ".litertlm", context.cacheDir).apply { writeText(payload) }
        val sha = DeviceRuntimeIdentity.hash(payload)
        val inventory = Inventory(LocalModelDescriptor("artifact", "Artifact", file.absolutePath, file.length(),
            file.lastModified(), sha, 4096, false, false, LocalModelValidation.VERIFIED_ARTIFACT))
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val counted = CountingDao(db.modelRuntimeProfileDao())
            val repo = ModelRuntimeProfileRepository(counted)
            val provider = LiteRtLocalProvider(context, inventory, storage, nativeResourceCoordinator = null,
                catalog = listOf(LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4.copy(id = "artifact", expectedBytes = file.length(), sha256 = sha)))
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
            val dbLookups = counted.lookups
            val verified = inventory.verifications
            val resolutions = inventory.resolutions
            val refreshes = inventory.refreshes
            repeat(2) { assertEquals(3000L, source.profile(ProviderIds.LITERT, "artifact")!!.ttftMs) }
            assertEquals(dbLookups, counted.lookups)
            assertEquals(verified, inventory.verifications)
            assertEquals(resolutions, inventory.resolutions)
            assertEquals(refreshes, inventory.refreshes)
            inventory.inventoryState.value = LocalModelInventoryState.Ready(listOf(inventory.descriptor), 999)
            assertNotNull(source.profile(ProviderIds.LITERT, "artifact"))
            assertEquals(dbLookups + 1, counted.lookups)
            provider.preferences.setContextOverride("artifact", 2048)
            assertNull(source.profile(ProviderIds.LITERT, "artifact"))
            assertEquals(dbLookups + 2, counted.lookups)
            assertNull(source.profile(ProviderIds.LITERT, "artifact"))
            assertEquals(dbLookups + 2, counted.lookups)
            provider.preferences.setContextOverride("artifact", null)
            provider.preferences.setDeveloperBackend("artifact", LocalComputeBackend.GPU)
            assertNull(source.profile(ProviderIds.LITERT, "artifact"))
            assertEquals(dbLookups + 3, counted.lookups)
            provider.preferences.clearDeveloperBackend("artifact")
            inventory.inventoryState.value = LocalModelInventoryState.Ready(listOf(inventory.descriptor.copy(id = "other")), 1000)
            assertNull(source.profile(ProviderIds.LITERT, "other"))
            assertEquals(dbLookups + 4, counted.lookups)
            inventory.inventoryState.value = LocalModelInventoryState.Ready(listOf(inventory.descriptor), 1001)
            val modified = file.lastModified()
            file.writeText("beta".repeat(300_000)); file.setLastModified(modified)
            inventory.verifyPhysicalArtifact("artifact")
            assertNull(source.profile(ProviderIds.LITERT, "artifact"))
            inventory.descriptor = inventory.descriptor.copy(sha256 = DeviceRuntimeIdentity.hash("beta".repeat(300_000)))
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
