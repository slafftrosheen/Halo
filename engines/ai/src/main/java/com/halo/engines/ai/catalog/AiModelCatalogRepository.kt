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
    fun invalidate(providerId: String) {}
    suspend fun restoreCached() {}
    suspend fun refresh()
    suspend fun refresh(providerId: String) = refresh()
}

/** Provider-neutral, last-known-good catalog. Provider failures are isolated. */
class DefaultAiModelCatalogRepository(
    private val providers: List<AiInferenceProvider>,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val storage: com.halo.sdk.runtime.Storage? = null,
) : AiModelCatalogRepository {
    private val _state = MutableStateFlow(AiModelCatalogState())
    override val state: StateFlow<AiModelCatalogState> = _state.asStateFlow()

    override fun invalidate(providerId: String) {
        _state.value = _state.value.copy(models = _state.value.models.filter { it.model.providerId != providerId },
            providerErrors = _state.value.providerErrors - providerId)
    }
    override suspend fun restoreCached() {
        val store = storage ?: return
        val restored = providers.filter { it.id != DefaultAiProviderRouter.LITERT_PROVIDER_ID }.flatMap { provider ->
            decodeModels(provider, store.getString(cacheKey(provider))).map { model ->
                model.toEntry(provider, ProviderStatus.UNCONFIGURED).copy(source = ModelCatalogueSource.REMOTE_CACHED)
            }
        }
        _state.value = _state.value.copy(models = (_state.value.models + restored).distinctBy { it.runtime.ref })
    }
    private fun cacheKey(provider: AiInferenceProvider): String {
        val configuration = (provider as? ProviderConfigurationReporting)?.configurationDescriptor
        val endpoint = configuration?.fields?.filter { it.kind == ProviderFieldKind.ENDPOINT }?.joinToString { storage?.getString(it.id).orEmpty() }.orEmpty()
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(endpoint.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        return "ai_model_catalog_v1_${provider.id}_$fingerprint"
    }
    private fun encodeModels(models: List<AiModel>): String {
        fun encode(text: String) = java.util.Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        return models.take(3000).joinToString("\n") { model ->
            listOf(encode(model.id), encode(model.displayName), model.inputCapabilities.joinToString(",") { it.name },
                model.contextLength?.toString().orEmpty(), model.supportsStreaming.toString(), model.supportsTools.toString(), model.isFree.toString(), model.capabilityEvidence.entries.joinToString(",") { "${it.key.name}:${it.value.name}" }).joinToString("|")
        }
    }
    private fun decodeModels(provider: AiInferenceProvider, raw: String?): List<AiModel> = raw?.take(2_000_000)?.lineSequence()?.take(3000)?.mapNotNull { line ->
        runCatching {
            val fields = line.split('|'); require(fields.size in 7..8)
            fun decode(index: Int) = String(java.util.Base64.getDecoder().decode(fields[index]), Charsets.UTF_8)
            AiModel(decode(0), decode(1), provider.id, fields[2].split(',').mapNotNull { name -> AiCapability.entries.firstOrNull { it.name == name } }.toSet(),
                fields[3].toLongOrNull(), fields[4].toBooleanStrict(), fields[5].toBooleanStrict(), fields[6].toBooleanStrict(), capabilityEvidence = fields.getOrNull(7)?.split(',')?.mapNotNull { item ->
                    val pair = item.split(':')
                    if (pair.size != 2) null else runCatching { AiCapability.valueOf(pair[0]) to CapabilityEvidence.valueOf(pair[1]) }.getOrNull()
                }?.toMap().orEmpty())
        }.getOrNull()
    }?.toList().orEmpty()

    private val catalogCache = ModelCatalogCache()

    private val refreshMutex = kotlinx.coroutines.sync.Mutex()
    override suspend fun refresh() { refreshProviders(providers) }
    override suspend fun refresh(providerId: String) { refreshProviders(providers.filter { it.id == providerId }) }

    private suspend fun refreshProviders(targets: List<AiInferenceProvider>) = refreshMutex.withLock {
        val ids = targets.mapTo(linkedSetOf()) { it.id }
        val configurations = targets.associate { it.id to cacheKey(it) }
        _state.value = _state.value.copy(refreshingProviders = ids)
        try {
            val results = coroutineScope {
                targets.map { provider -> async {
                    try {
                        val models = provider.refreshModels()
                        if (configurations[provider.id] != cacheKey(provider)) ProviderResult(provider.id, null, "Provider configuration changed; refresh again")
                        else ProviderResult(provider.id, models.map { it.toEntry(provider, provider.getStatus()) }, null)
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
            results.forEach { result -> result.entries?.let { entries ->
                targets.firstOrNull { it.id == result.providerId }?.takeIf { it.id != DefaultAiProviderRouter.LITERT_PROVIDER_ID }?.let { provider ->
                    storage?.putString(cacheKey(provider), encodeModels(entries.map { it.model }))
                }
            } }
        } finally { _state.value = _state.value.copy(refreshingProviders = emptySet()) }
    }

    private fun lastSourceFor(providerId: String, merged: List<AiModelCatalogEntry>): ModelCatalogueSource {
        return merged.filter { it.model.providerId == providerId }.lastOrNull()?.source ?: ModelCatalogueSource.REMOTE_CACHED
    }

    fun cachedModels(providerId: String): List<AiModel> = catalogCache.get(providerId)?.models.orEmpty()

    private fun AiModel.toEntry(provider: AiInferenceProvider, status: ProviderStatus): AiModelCatalogEntry {
        val location = (provider as? ProviderConfigurationReporting)?.configurationDescriptor?.location ?: executionLocation

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
                requiresCredential = (provider as? ProviderConfigurationReporting)?.configurationDescriptor?.fields?.any { it.kind == ProviderFieldKind.SECRET || it.kind == ProviderFieldKind.OAUTH_ACTION }
                    ?: (location == AiExecutionLocation.REMOTE),
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
                    else -> (provider as? ProviderConfigurationReporting)?.configurationDescriptor?.protocol?.let { protocol -> AiWireProtocol.entries.firstOrNull { it.name == protocol } }
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
