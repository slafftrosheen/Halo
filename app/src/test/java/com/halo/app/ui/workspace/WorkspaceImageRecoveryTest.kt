package com.halo.app.ui.workspace

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.ai.*
import com.halo.app.data.AppDatabase
import com.halo.app.data.workspace.NotebookAssetRepository
import com.halo.app.online.OnlineProblem
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WorkspaceImageRecoveryTest {
    @Test fun unreadableReferenceStaysInEditModeAndCannotGenerateAfterCatalogueRefresh() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val storage = MemoryStorage()
            PrivacyModeStore(storage).set(PrivacyMode.CLOUD)
            val provider = CreditBlockedProvider()
            val studio = WorkspaceImageStudio(scope, WorkspaceImageService(Router(provider), storage),
                NotebookAssetRepository(context, database), save = { _, _ -> fail("No image to save") })
            studio.prepare(com.halo.app.data.workspace.NoteAttachment("missing", "note", "asset", 0, null,
                "IMAGE", "image/png", "MANAGED_FILE", java.io.File(context.cacheDir, "missing-image.png").absolutePath,
                true, null, null, null, null, null))
            val state = withTimeout(5_000) { studio.state.first { !it.loading && it.referenceUnavailable } }
            assertTrue(state.editing) // A missing display name must not turn Edit into Create.
            assertNull(state.referenceBytes)
            studio.prompt("Synthetic edit")
            studio.generate()
            assertNull(studio.state.value.spendPreview)
            assertFalse(studio.state.value.busy)
            assertEquals(0, provider.calls)
        } finally { scope.cancel(); database.close() }
    }

    @Test fun providerCreditRejectionKeepsDraftAndOffersCreditRecoveryWithoutRetry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val storage = MemoryStorage()
            PrivacyModeStore(storage).set(PrivacyMode.CLOUD)
            val provider = CreditBlockedProvider()
            val service = WorkspaceImageService(Router(provider), storage)
            val studio = WorkspaceImageStudio(scope, service, NotebookAssetRepository(context, database), save = { _, _ -> fail("No image to save") })
            studio.refresh()
            withTimeout(5_000) { studio.state.first { !it.loading && it.selected != null } }
            studio.prompt("Synthetic mint circle")
            studio.generate()
            withTimeout(5_000) { studio.state.first { !it.busy && it.spendPreview != null } }
            assertEquals(0, provider.calls)
            studio.confirmSpend(BigDecimal("0.10"))
            val state = withTimeout(5_000) { studio.state.first { !it.busy && it.onlineError != null } }
            assertEquals(OnlineProblem.CREDIT_LIMIT, state.onlineError)
            assertEquals("Synthetic mint circle", state.prompt)
            assertNull(state.result)
            assertNull(state.spendPreview)
            assertEquals("openrouter", state.selected?.providerId)
            assertEquals(1, provider.calls)
        } finally { scope.cancel(); database.close() }
    }

    private class CreditBlockedProvider : AiImageProvider {
        override val id = "openrouter"
        override val displayName = "OpenRouter"
        override val isLocal = false
        override val status = MutableStateFlow(ProviderStatus.READY)
        var calls = 0
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT, AiCapability.IMAGE)
        override suspend fun listModels() = emptyList<AiModel>()
        override suspend fun listImageModels() = listOf(AiImageModel("synthetic/image"))
        override suspend fun generateImage(request: AiImageRequest): AiImageResult {
            calls++
            throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, "Provider HTTP 402", AiFailureDetails(httpStatus = 402))
        }
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = error("Unused")
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
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
