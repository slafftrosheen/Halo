package com.halo.sdk.provider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preset definitions for remote/openai-compatible providers and the OpenCode
 * companion. These are declared so the app can present a consistent provider
 * matrix without each preset carrying its own bespoke HTTP stack.
 */
object RemoteProviderPresets {
    /** Official tool-use tables checked 2026-11-16. Hints supplement missing metadata only;
     * a model must still be returned by the live catalog and explicit server flags win. */
    fun documentedToolModels(providerId: String): List<AiModel> = (when (providerId) {
        RemoteProviderIds.GROQ -> listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b", "openai/gpt-oss-safeguard-20b",
            "qwen/qwen3.8-27b", "minimaxai/minimax-m2.7", "meta-models/muse-spark-open", "llama-3.3-70b-versatile", "llama-3.1-8b-instant")
        RemoteProviderIds.CEREBRAS -> listOf("gpt-oss-120b", "zai-glm-4.7", "llama3.1-8b")
        RemoteProviderIds.XAI -> listOf("grok-4.7", "grok-4.20-multi-agent-beta-0309")
        else -> emptyList()
    }).map { AiModel(it, it, providerId, setOf(AiCapability.TEXT), null, supportsStreaming = true, supportsTools = true) }

    val groq = RemoteProviderDescriptor(
        id = RemoteProviderIds.GROQ,
        displayName = "Groq",
        protocol = AiWireProtocol.OPENAI_CHAT_COMPLETIONS,
        authKinds = setOf(ProviderAuthKind.API_KEY),
        accessKind = ProviderAccessKind.FREE_TIER,
        modelDiscovery = ModelDiscoveryMode.REMOTE_CATALOG,
        capabilities = setOf(AiCapability.TEXT, AiCapability.STREAMING, AiCapability.TOOLS),
        freeModelHint = "gpt-oss",
    )

    val cerebras = RemoteProviderDescriptor(
        id = RemoteProviderIds.CEREBRAS,
        displayName = "Cerebras",
        protocol = AiWireProtocol.OPENAI_CHAT_COMPLETIONS,
        authKinds = setOf(ProviderAuthKind.API_KEY),
        accessKind = ProviderAccessKind.FREE_TIER,
        modelDiscovery = ModelDiscoveryMode.REMOTE_CATALOG,
        capabilities = setOf(AiCapability.TEXT, AiCapability.STREAMING, AiCapability.TOOLS),
    )

    val opencodeZen = RemoteProviderDescriptor(
        id = RemoteProviderIds.OPENCODE_ZEN,
        displayName = "OpenCode Zen",
        protocol = AiWireProtocol.OPENAI_CHAT_COMPLETIONS,
        authKinds = setOf(ProviderAuthKind.API_KEY),
        accessKind = ProviderAccessKind.MIXED,
        modelDiscovery = ModelDiscoveryMode.REMOTE_CATALOG,
        capabilities = setOf(AiCapability.TEXT, AiCapability.STREAMING, AiCapability.TOOLS),
    )

    val xai = RemoteProviderDescriptor(
        id = RemoteProviderIds.XAI,
        displayName = "xAI",
        protocol = AiWireProtocol.OPENAI_CHAT_COMPLETIONS,
        authKinds = setOf(ProviderAuthKind.API_KEY),
        accessKind = ProviderAccessKind.PAY_AS_YOU_GO,
        modelDiscovery = ModelDiscoveryMode.REMOTE_CATALOG,
        capabilities = setOf(AiCapability.TEXT, AiCapability.IMAGE, AiCapability.STREAMING, AiCapability.TOOLS),
    )

    val opencodeCompanion = RemoteProviderDescriptor(
        id = RemoteProviderIds.OPENCODE_COMPANION,
        displayName = "OpenCode Companion",
        protocol = AiWireProtocol.OPENAI_CHAT_COMPLETIONS,
        authKinds = setOf(ProviderAuthKind.COMPANION),
        accessKind = ProviderAccessKind.CUSTOM,
        modelDiscovery = ModelDiscoveryMode.REMOTE_CATALOG,
        capabilities = setOf(AiCapability.TEXT, AiCapability.IMAGE, AiCapability.STREAMING, AiCapability.TOOLS),
        executionLocation = AiExecutionLocation.LOCAL_NETWORK,
    )

    val customOpenAiCompatible = RemoteProviderDescriptor(
        id = RemoteProviderIds.CUSTOM_OPENAI_COMPATIBLE,
        displayName = "Custom OpenAI-compatible",
        protocol = AiWireProtocol.OPENAI_CHAT_COMPLETIONS,
        authKinds = setOf(ProviderAuthKind.NONE, ProviderAuthKind.API_KEY, ProviderAuthKind.BEARER_TOKEN),
        accessKind = ProviderAccessKind.CUSTOM,
        modelDiscovery = ModelDiscoveryMode.REMOTE_CATALOG,
        capabilities = setOf(AiCapability.TEXT, AiCapability.STREAMING, AiCapability.TOOLS),
        executionLocation = AiExecutionLocation.LOCAL_NETWORK,
    )
}

/**
 * UI-readable presence for a remote provider.
 *
 * This is separate from provider status because OAuth/companion flows expose
 * intermediate UI states that do not map cleanly onto provider readiness.
 */
data class RemoteProviderPresence(
    val descriptor: RemoteProviderDescriptor,
    val authInfo: ProviderAuthInfo,
    val status: ProviderStatus,
    val isConfigured: Boolean,
    val isSelected: Boolean,
)

/**
 * Lightweight in-memory remote catalog cache for providers that support remote
 * model discovery.
 *
 * This keeps last-known catalog available during temporary network failure
 * instead of discarding it immediately.
 */
class ModelCatalogCache(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = 15 * 60 * 1_000L,
) {
    private val cache = mutableMapOf<String, CachedCatalog>()

    data class CachedCatalog(
        val models: List<AiModel>,
        val retrievedAtMs: Long,
        val source: ModelCatalogueSource,
    )

    fun get(providerId: String): CachedCatalog? {
        val entry = cache[providerId] ?: return null
        return if (nowMs() - entry.retrievedAtMs < ttlMs) entry else null
    }

    fun put(providerId: String, models: List<AiModel>, source: ModelCatalogueSource) {
        cache[providerId] = CachedCatalog(models, nowMs(), source)
    }

    fun invalidate(providerId: String) {
        cache.remove(providerId)
    }
}

/**
 * Live presence registry for remote providers.
 *
 * This is intentionally separate from the inference provider registry so UI can
 * show connect/test/refresh actions and auth state without triggering provider
 * IO on every composition.
 */
class RemoteProviderPresenceRegistry {
    private val presences = mutableMapOf<String, MutableStateFlow<RemoteProviderPresence>>()

    fun register(presence: RemoteProviderPresence) {
        presences.getOrPut(presence.descriptor.id) { MutableStateFlow(presence) }.value = presence
    }

    fun presence(providerId: String): StateFlow<RemoteProviderPresence?> {
        val flow = presences[providerId] ?: return MutableStateFlow(null)
        return flow.asStateFlow()
    }

    fun allPresences(): List<RemoteProviderPresence> = presences.values.map { it.value }.filterNotNull()
}
