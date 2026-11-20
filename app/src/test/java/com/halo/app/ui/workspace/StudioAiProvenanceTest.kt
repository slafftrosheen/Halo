package com.halo.app.ui.workspace

import android.app.Application
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.ai.*
import com.halo.app.data.AppDatabase
import com.halo.app.data.workspace.NotebookAssetRepository
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class)
class StudioAiProvenanceTest {
    @Test fun deferredReviewKeepsScreenMemoryAndOriginalRevisionAtActualGeneration() = runBlocking {
        exercise()
    }
    @Test fun staleOrBarredOriginsCannotReachGenerationOrReceiveFreshAuthority() = runBlocking {
        exercise(revokeBeforeReview = true)
    }
    @Test fun authorityRevokedAfterReviewCannotReachGeneration() = runBlocking {
        exercise(revokeBeforeSpend = true)
    }
    private suspend fun exercise(revokeBeforeReview: Boolean = false, revokeBeforeSpend: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val modes = PrivacyModeStore(MemoryStorage()).apply {
            remoteAllowed = { true }
            configuredProviders = { setOf("fixture") }
            acknowledgeSharing()
            set(PrivacyMode.CLOUD)
        }
        val storage = modes.storage
        val origin = RemoteDispatchProvenance(setOf(RemoteDataCategory.SCREEN, RemoteDataCategory.MEMORIES), modes.revision)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val bytes = try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() } finally { bitmap.recycle() }
        var generated: RemoteDispatchProvenance? = null
        var calls = 0
        val provider = object : AiImageProvider {
            override val id = "fixture"
            override val displayName = "Fixture"
            override val isLocal = false
            override val status = MutableStateFlow(ProviderStatus.READY)
            override suspend fun getCapabilities() = setOf(AiCapability.TEXT, AiCapability.IMAGE)
            override suspend fun listModels() = emptyList<AiModel>()
            override suspend fun listImageModels() = listOf(AiImageModel("image", supportsEditing = true))
            override suspend fun generateImage(request: AiImageRequest): AiImageResult {
                calls++; generated = RemoteDispatchContext.provenance.get()
                throw RemoteDispatchBlocked() // No remote transport or image publication in this fixture.
            }
            override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = error("Unused")
            override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
        }
        try {
            val studio = WorkspaceImageStudio(scope, WorkspaceImageService(Router(provider), storage,
                allowReference = { true }, privacyModes = modes.mode),
                NotebookAssetRepository(context, database), save = { _, _ -> fail("No result") })
            studio.prepareReference(bytes, "image/png", "project", "Project", origin)
            val ready = withTimeout(5000) { studio.state.first { !it.loading } }
            assertFalse(ready.referenceUnavailable)
            assertNotNull(ready.error, ready.selected)
            if (revokeBeforeReview) modes.invalidateAuthority()
            studio.prompt("Derived proposal")
            studio.generate()
            withTimeout(5000) { studio.state.first { !it.busy } }
            assertEquals(0, calls)
            if (!revokeBeforeReview) {
                assertNotNull(studio.state.value.spendPreview)
                if (revokeBeforeSpend) modes.invalidateAuthority()
                studio.confirmSpend(BigDecimal("0.10"))
                withTimeout(5000) { studio.state.first { !it.busy } }
                assertEquals(if (revokeBeforeSpend) 0 else 1, calls)
                assertEquals(if (revokeBeforeSpend) null else origin, generated)
            } else {
                assertNull(studio.state.value.spendPreview)
                assertNotNull(studio.state.value.error)
            }
            studio.discardDraft()
            assertNull(studio.state.value.referenceBytes)
            assertEquals("", studio.state.value.prompt)
        } finally { scope.cancel(); database.close() }
    }
    private class Router(private val provider: AiProvider) : AiProviderRouter {
        override val selectedProviderId = MutableStateFlow<String?>(provider.id)
        override val selectedModelId = MutableStateFlow<String?>("chat-model")
        override val routingPolicy = MutableStateFlow(AiRoutingPolicy.EXPLICIT)
        override fun selectProvider(id: String) { selectedProviderId.value = id }
        override fun selectModel(modelId: String?) { selectedModelId.value = modelId }
        override fun getAllProviders() = listOf(provider)
        override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec): AiExecutionTarget = error("Unused")
        override suspend fun getEligibleProvider(request: AiRequest) = provider
        override suspend fun execute(request: AiRequest, context: AiContext): AiResponse = error("Unused")
        override fun start() = Unit
        override fun stop() = Unit
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
        override fun clear() = values.clear()
    }
}
