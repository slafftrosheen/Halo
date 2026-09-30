package com.halo.engines.ai.catalog

import com.halo.engines.ai.DefaultAiProviderRouter
import com.halo.sdk.provider.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AiModelCatalogRepository {
    val state: StateFlow<AiModelCatalogState>
    suspend fun refresh()
    suspend fun refresh(providerId: String) = refresh()
}

/** Provider-neutral, last-known-good catalog. Provider failures are isolated. */
class DefaultAiModelCatalogRepository(
    private val providers: List<AiInferenceProvider>,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : AiModelCatalogRepository {
    private val _state = MutableStateFlow(AiModelCatalogState())
    override val state: StateFlow<AiModelCatalogState> = _state.asStateFlow()

    private val catalogCache = ModelCatalogCache()

    private val refreshMutex = kotlinx.coroutines.sync.Mutex()
    override suspend fun refresh() { refreshProviders(providers) }
    override suspend fun refresh(providerId: String) { refreshProviders(providers.filter { it.id == providerId }) }

    private suspend fun refreshProviders(targets: List<AiInferenceProvider>) = refreshMutex.withLock {
        val ids = targets.mapTo(linkedSetOf()) { it.id }
        _state.value = _state.value.copy(refreshingProviders = ids)
        try {
            val results = coroutineScope {
                targets.map { provider -> async {
                    try {
                        val models = provider.listModels()
                        ProviderResult(provider.id, models.map { it.toEntry(provider, provider.getStatus()) }, null)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { ProviderResult(provider.id, null, "Model metadata could not be refreshed") }
                } }.awaitAll()
            }
            val previous = _state.value
            val merged = previous.models.filter { it.model.providerId !in ids } + results.flatMap { result ->
                result.entries ?: previous.models.filter { it.model.providerId == result.providerId }
                    .map { it.copy(source = if (it.runtime.location == AiExecutionLocation.REMOTE) ModelCatalogueSource.REMOTE_CACHED else it.source) }
            }
            _state.value = previous.copy(
                models = merged.sortedWith(compareBy<AiModelCatalogEntry> { it.runtime.location.ordinal }.thenBy { it.model.displayName.lowercase() }),
                refreshingProviders = emptySet(),
                providerErrors = previous.providerErrors.filterKeys { it !in ids } + results.mapNotNull { result -> result.error?.let { result.providerId to it } }.toMap(),
                refreshedAtMs = nowMs(),
            )
            results.forEach { result -> result.entries?.let { catalogCache.put(result.providerId, it.map { it.model }, lastSourceFor(result.providerId, merged)) } }
        } finally { _state.value = _state.value.copy(refreshingProviders = emptySet()) }
    }

    private fun lastSourceFor(providerId: String, merged: List<AiModelCatalogEntry>): ModelCatalogueSource {
        return merged.filter { it.model.providerId == providerId }.lastOrNull()?.source ?: ModelCatalogueSource.REMOTE_CACHED
    }

    fun cachedModels(providerId: String): List<AiModel> = catalogCache.get(providerId)?.models.orEmpty()

    private fun AiModel.toEntry(provider: AiInferenceProvider, status: ProviderStatus): AiModelCatalogEntry {
        val location = when (provider.id) {
            DefaultAiProviderRouter.LITERT_PROVIDER_ID -> AiExecutionLocation.ON_DEVICE
            "provider.ollama" -> AiExecutionLocation.LOCAL_NETWORK
            else -> AiExecutionLocation.REMOTE
        }
        val live = status.state == ProviderStatus.State.READY
        val source = when (location) {
            AiExecutionLocation.ON_DEVICE -> ModelCatalogueSource.LOCAL_INSTALLED
            AiExecutionLocation.LOCAL_NETWORK -> ModelCatalogueSource.LAN_LIVE
            AiExecutionLocation.REMOTE -> if (live) ModelCatalogueSource.REMOTE_LIVE else ModelCatalogueSource.REMOTE_CACHED
        }
        return AiModelCatalogEntry(
            model = this,
            runtime = AiModelRuntimeMetadata(
                ref = AiModelRef(provider.id, id),
                location = location,
                installed = if (location == AiExecutionLocation.ON_DEVICE) true else null,
                resident = null,
                requiresNetwork = location != AiExecutionLocation.ON_DEVICE,
                requiresCredential = location == AiExecutionLocation.REMOTE,
                costClass = when {
                    location == AiExecutionLocation.ON_DEVICE -> AiCostClass.LOCAL
                    isFree -> AiCostClass.FREE
                    location == AiExecutionLocation.REMOTE -> AiCostClass.PAID
                    else -> AiCostClass.UNKNOWN
                },
                wireProtocol = when (provider.id) {
                    DefaultAiProviderRouter.LITERT_PROVIDER_ID -> AiWireProtocol.LITERT_NATIVE
                    "provider.ollama" -> AiWireProtocol.OLLAMA
                    "openrouter" -> AiWireProtocol.OPENAI_CHAT_COMPLETIONS
                    "openai" -> AiWireProtocol.OPENAI_CHAT_COMPLETIONS
                    "anthropic" -> AiWireProtocol.ANTHROPIC_MESSAGES
                    "gemini" -> AiWireProtocol.GEMINI_GENERATE_CONTENT
                    else -> null
                },
            ),
            kind = if (id == "openrouter/free" || id.startsWith("@preset/")) AiModelKind.ROUTER else AiModelKind.CONCRETE,
            source = source,
        )
    }

    private data class ProviderResult(
        val providerId: String,
        val entries: List<AiModelCatalogEntry>?,
        val error: String?,
    )
}
