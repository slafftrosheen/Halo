package com.halo.app.ai

import com.halo.engines.ai.catalog.AiModelCatalogRepository
import com.halo.providers.litert.LiteRtLocalProvider.ProviderIds
import com.halo.providers.litert.models.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

class LocalModelCatalogRepository(private val delegate: AiModelCatalogRepository,
    private val manager: LiteRtModelManager, scope: CoroutineScope,
    private val provider: () -> com.halo.providers.litert.LiteRtLocalProvider?) : AiModelCatalogRepository {
    override val state = combine(delegate.state, manager.inventoryState) { remote, inventory ->
        val installed = (inventory as? LocalModelInventoryState.Ready)?.models.orEmpty().filter { it.valid }.map { it.id }.toSet()
        val entries = manager.getAllCatalogModels().filter { it.id !in installed }.map { model ->
            AiModelCatalogEntry(
                model = AiModel(id = model.id, displayName = model.displayName, providerId = ProviderIds.LITERT,
                    executionLocation = AiExecutionLocation.ON_DEVICE, inputCapabilities = buildSet {
                        add(AiCapability.TEXT); if (model.supportsVision) add(AiCapability.IMAGE)
                    }, contextLength = model.contextWindowTokens.toLong(), supportsStreaming = true,
                    supportsTools = model.supportsTools),
                runtime = AiModelRuntimeMetadata(ref = AiModelRef(ProviderIds.LITERT, model.id),
                    location = AiExecutionLocation.ON_DEVICE, installed = false, resident = false,
                    requiresNetwork = false, requiresCredential = false, costClass = AiCostClass.LOCAL),
                source = ModelCatalogueSource.LOCAL_DOWNLOADABLE)
        }
        val installedEntries = provider()?.listModels().orEmpty().filter { it.id in installed }.map { model ->
            AiModelCatalogEntry(model = model,
                runtime = AiModelRuntimeMetadata(ref = AiModelRef(ProviderIds.LITERT, model.id),
                    location = AiExecutionLocation.ON_DEVICE, installed = true,
                    resident = provider()?.modelSession?.loadedModelId == model.id,
                    requiresNetwork = false, requiresCredential = false, costClass = AiCostClass.LOCAL),
                source = ModelCatalogueSource.LOCAL_INSTALLED)
        }
        remote.copy(models = (remote.models.filter { it.model.providerId != ProviderIds.LITERT } + installedEntries + entries).distinctBy { it.runtime.ref })
    }.stateIn(scope, SharingStarted.Eagerly, delegate.state.value)
    override fun invalidate(providerId: String) = delegate.invalidate(providerId)
    override suspend fun restoreCached() = delegate.restoreCached()
    override suspend fun refresh() { manager.refresh(ModelInventoryRefreshReason.USER_REQUEST); delegate.refresh() }
    override suspend fun refresh(providerId: String) {
        if (providerId == ProviderIds.LITERT) manager.refresh(ModelInventoryRefreshReason.USER_REQUEST)
        delegate.refresh(providerId)
    }
}
