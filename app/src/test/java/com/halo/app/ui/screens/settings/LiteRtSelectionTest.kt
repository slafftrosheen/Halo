package com.halo.app.ui.screens.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.engines.ai.DefaultAiProviderRouter
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.LiteRtModelCatalog
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.providers.litert.models.LocalModelInventoryState
import com.halo.providers.litert.models.LocalModelValidation
import com.halo.providers.litert.models.ModelInventoryRefreshReason
import com.halo.runtime.cognition.context.ContextAssembler
import com.halo.runtime.cognition.context.ContextAssemblyResult
import com.halo.runtime.cognition.conversation.ConversationStore
import com.halo.runtime.cognition.conversation.ConversationTurn
import com.halo.runtime.cognition.session.CognitiveSession
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiRoutingPolicy
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloDispatchers
import com.halo.sdk.runtime.HaloEvent
import com.halo.sdk.runtime.RuntimeSnapshot
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.Storage
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression guard for the Model Browser selection bug: selecting a LiteRT model must never bind
 * that model ID to whatever provider happened to be selected before (OpenRouter, Ollama, …).
 */
@RunWith(RobolectricTestRunner::class)
class LiteRtSelectionTest {

    private val qwenId = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4_ID

    private lateinit var storage: InMemoryStorage
    private lateinit var router: DefaultAiProviderRouter
    private lateinit var viewModel: IntelligenceViewModel

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        storage = InMemoryStorage()
        val openRouter = FakeInferenceProvider(id = "openrouter", isLocal = false)
        val litert = LiteRtLocalProvider(
            context = context,
            inventoryRepository = FakeInventory(qwenId),
            storage = storage,
            nativeResourceCoordinator = null,
        )
        router = DefaultAiProviderRouter(
            providers = listOf(openRouter, litert),
            storage = storage,
            eventBus = null,
            logger = null,
            defaultInferenceProviderId = "openrouter",
        )
        viewModel = IntelligenceViewModel(
            snapshotManager = FakeSnapshotSource(),
            aiProviderRouter = router,
            cognitiveSession = CognitiveSession(
                eventBus = FakeEventBus(),
                providerRouter = router,
                conversationStore = FakeConversationStore(),
                contextAssembler = FakeContextAssembler(),
            ),
            storage = storage,
            modelManager = null,
            dispatchers = HaloDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined),
            inferenceLedger = null,
        )
    }

    @Test
    fun `selecting an on-device model rebinds provider and model atomically`() = runTest {
        assertEquals("openrouter", router.selectedProviderId.value)

        viewModel.selectLocalModel(qwenId)

        assertEquals(ProviderIds.LITERT, router.selectedProviderId.value)
        assertEquals(qwenId, router.selectedModelId.value)
        assertEquals(ProviderIds.LITERT, storage.getString("active_ai_provider"))
        assertEquals(qwenId, storage.getString("active_ai_model"))
        assertEquals(qwenId, storage.getString("litert_model"))
        assertEquals(AiRoutingPolicy.EXPLICIT, router.routingPolicy.value)
    }

    @Test
    fun `selecting a local model does not pollute the previous provider's model key`() = runTest {
        viewModel.selectLocalModel(qwenId)

        assertNotEquals(qwenId, storage.getString("openrouter_model"))
        assertNotEquals(qwenId, storage.getString("ollama_model"))
        assertEquals(qwenId, storage.getString("litert_model"))
    }

    @Test
    fun `the generic select path routes a local model to the on-device provider`() = runTest {
        // Provider currently selected is OpenRouter, but the model belongs to LiteRT.
        viewModel.selectModel("openrouter/free")
        assertEquals("openrouter/free", router.selectedModelId.value)
        assertEquals("openrouter", router.selectedProviderId.value)

        router.selectProvider(ProviderIds.LITERT)
        viewModel.selectLocalModel(qwenId)

        assertEquals(ProviderIds.LITERT, router.selectedProviderId.value)
        assertEquals(qwenId, router.selectedModelId.value)
        assertNotNull(storage.getString("litert_model"))
    }

    @Test
    fun `legacy llama provider ids are migrated on restore and never reselected`() = runTest {
        storage.putString("active_ai_provider", "provider.llamacpp.native")
        storage.putString("litert_model", qwenId)

        val migrated = DefaultAiProviderRouter(
            providers = listOf(FakeInferenceProvider(id = "openrouter", isLocal = false)),
            storage = storage,
            eventBus = null,
            logger = null,
            defaultInferenceProviderId = "openrouter",
        )

        assertEquals("provider.litert.local", storage.getString("active_ai_provider"))
        assertEquals("openrouter", migrated.selectedProviderId.value)
    }

    // ---------------------------------------------------------------------------------------
    // Fakes
    // ---------------------------------------------------------------------------------------

    private class InMemoryStorage : Storage {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?): String? =
            (values[key] as? String) ?: defaultValue
        override fun putString(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }
        override fun getInt(key: String, defaultValue: Int): Int = (values[key] as? Int) ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            (values[key] as? Boolean) ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }

    private class FakeInventory(private val modelId: String) : LocalModelInventoryRepository {
        override val inventoryState: StateFlow<LocalModelInventoryState> = MutableStateFlow(
            LocalModelInventoryState.Ready(
                listOf(
                    LocalModelDescriptor(
                        id = modelId,
                        displayName = "Qwen 3 0.6B Dynamic INT4",
                        path = "/tmp/$modelId.litertlm",
                        bytes = 344_671_744L,
                        modifiedAt = 0L,
                        sha256 = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4.sha256,
                        contextWindowTokens = 4096,
                        supportsVision = false,
                        supportsTools = false,
                        validation = LocalModelValidation.VERIFIED_ARTIFACT,
                    )
                ),
                System.currentTimeMillis(),
            )
        )
        override suspend fun refresh(reason: ModelInventoryRefreshReason) {}
        override suspend fun resolveModel(modelId: String): LocalModelDescriptor? =
            (inventoryState.value as? LocalModelInventoryState.Ready)?.models?.firstOrNull { it.id == modelId }
        override fun invalidate(reason: String) {}
    }

    private class FakeInferenceProvider(
        override val id: String,
        override val isLocal: Boolean,
    ) : AiInferenceProvider {
        override val displayName: String = id
        override val status: StateFlow<ProviderStatus> = MutableStateFlow(
            ProviderStatus(ProviderStatus.State.READY, "ready")
        )
        override suspend fun getStatus(): ProviderStatus = status.value
        override suspend fun getCapabilities(): Set<AiCapability> = setOf(AiCapability.TEXT, AiCapability.STREAMING)
        override suspend fun listModels(): List<AiModel> = emptyList()
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse =
            AiResponse(text = "ok", providerId = id)
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
        override suspend fun testConnection(): Boolean = true
        @Suppress("unused")
        fun routingPolicy(): AiRoutingPolicy = AiRoutingPolicy.EXPLICIT
    }

    private class FakeSnapshotSource : RuntimeSnapshotSource {
        override val snapshot: StateFlow<RuntimeSnapshot> = MutableStateFlow(
            RuntimeSnapshot(
                runtimeVersion = "test",
                buildNumber = 1,
                startedAt = Instant.now(),
                health = com.halo.sdk.runtime.RuntimeHealth(
                    healthy = true,
                    uptime = Duration.ZERO,
                    degraded = false,
                    lastFailure = null,
                ),
                runtimeState = com.halo.sdk.runtime.RuntimeState.Running,
                speech = com.halo.sdk.runtime.SpeechSessionState.Idle,
                engines = emptyList(),
                timeline = emptyList(),
                providers = emptyList(),
                blackboard = com.halo.sdk.runtime.BlackboardSnapshot(factCount = 0, recentFacts = emptyList()),
            )
        )
    }

    private class FakeEventBus : EventBus {
        override suspend fun publish(event: HaloEvent) {}
        override fun events(): Flow<HaloEvent> = emptyFlow()
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
    }

    private class FakeConversationStore : ConversationStore {
        override val turns: StateFlow<List<ConversationTurn>> = MutableStateFlow(emptyList())
        override suspend fun append(
            userText: String,
            assistantText: String,
            metadata: Map<String, String>,
        ): ConversationTurn = ConversationTurn("test", userText, assistantText, 0L)
        override suspend fun clear() {}
        override fun recentMessages(maxTurns: Int): List<AIMessage> = emptyList()
    }

    private class FakeContextAssembler : ContextAssembler {
        override suspend fun assemble(
            policy: AiContextPolicy,
            capabilities: Set<AiCapability>,
        ): ContextAssemblyResult = ContextAssemblyResult(context = AiContext.EMPTY)
    }
}
