package com.halo.engines.ai.catalog

import com.halo.sdk.provider.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AiModelCatalogRepository {
    val state: StateFlow<AiModelCatalogState>
    suspend fun refresh()
}

/** Provider-neutral, last-known-good catalog. Provider failures are isolated. */
class DefaultAiModelCatalogRepository(
    private val providers: List<AiInferenceProvider>,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : AiModelCatalogRepository {
    private val _state = MutableStateFlow(AiModelCatalogState())
    override val state: StateFlow<AiModelCatalogState> = _state.asStateFlow()

    override suspend fun refresh() = coroutineScope {
        val ids = providers.mapTo(linkedSetOf()) { it.id }
        _state.value = _state.value.copy(refreshingProviders = ids)
        val results = providers.map { provider ->
            async {
                try {
                    val models = provider.listModels()
                    val status = provider.getStatus()
                    ProviderResult(provider.id, models.map { it.toEntry(provider, status) }, null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    ProviderResult(provider.id, null, error.message ?: error::class.simpleName ?: "Catalog refresh failed")
                }
            }
        }.awaitAll()

        val previous = _state.value.models.groupBy { it.model.providerId }
        val merged = results.flatMap { result -> result.entries ?: previous[result.providerId].orEmpty() }
        _state.value = AiModelCatalogState(
            models = merged.sortedWith(compareBy<AiModelCatalogEntry> { it.runtime.location.ordinal }.thenBy { it.model.displayName.lowercase() }),
            refreshingProviders = emptySet(),
            providerErrors = results.mapNotNull { it.error?.let { message -> it.providerId to message } }.toMap(),
            refreshedAtMs = nowMs(),
        )
    }

    private fun AiModel.toEntry(provider: AiInferenceProvider, status: ProviderStatus): AiModelCatalogEntry {
        val location = when (provider.id) {
            "provider.litert.local", "provider.llamacpp.native" -> AiExecutionLocation.ON_DEVICE
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
                    "provider.litert.local", "provider.llamacpp.native" -> AiWireProtocol.LITERT_NATIVE
                    "provider.ollama" -> AiWireProtocol.OLLAMA
                    "openrouter" -> AiWireProtocol.OPENAI_CHAT_COMPLETIONS
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
