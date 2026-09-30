package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ai.ProviderControlRepository
import com.halo.sdk.provider.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

/** Filter/projection work happens off the composition thread. No catalogue fetch on entry. */
enum class ModelCostFilter { ALL, FREE, PAID }

data class ModelBrowserFilter(val query: String = "", val provider: String? = null,
    val location: AiExecutionLocation? = null, val capability: AiCapability? = null, val cost: ModelCostFilter = ModelCostFilter.ALL, val role: AiModelRole? = null)
data class ModelBrowserState(val entries: List<AiModelCatalogEntry> = emptyList(), val refreshing: Boolean = false, val errors: Boolean = false)
class ModelBrowserViewModel(val control: ProviderControlRepository) : ViewModel() {
    val filter = MutableStateFlow(ModelBrowserFilter())
    val state = combine(control.catalogue.state, filter, control.router.selectedProviderId, control.router.selectedModelId) { catalogue, f, provider, model ->
        val visible = catalogue.models.filter { entry ->
            (f.provider == null || entry.model.providerId == f.provider) &&
                (f.location == null || entry.runtime.location == f.location) &&
                (f.capability == null || supports(entry.model, f.capability)) &&
                (f.cost == ModelCostFilter.ALL || (f.cost == ModelCostFilter.FREE && entry.model.isFree) || (f.cost == ModelCostFilter.PAID && !entry.model.isFree && entry.runtime.location == AiExecutionLocation.REMOTE)) && compatible(entry, f.role) &&
                (f.query.isBlank() || (entry.model.displayName + " " + entry.model.id + " " + entry.model.providerId).contains(f.query, ignoreCase = true))
        }.sortedBy { if (it.model.providerId == provider && it.model.id == model) 0 else if (it.runtime.location == AiExecutionLocation.ON_DEVICE) 1 else 2 }
        ModelBrowserState(visible, catalogue.refreshingProviders.isNotEmpty(), catalogue.providerErrors.isNotEmpty())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelBrowserState())
    fun enter(provider: String?, role: AiModelRole?) { filter.value = ModelBrowserFilter(provider = provider, role = role) }
    fun pick(entry: AiModelCatalogEntry) {
        if (control.rows.value.firstOrNull { it.descriptor.providerId == entry.model.providerId }?.configured != true) return
        val role = filter.value.role
        if (role == null || role == AiModelRole.CHAT) control.router.selectTarget(entry.runtime.ref)
        else control.router.setRoleBinding(AiRoleBinding(role, entry.model.providerId, entry.model.id, AiRoleSelection.EXPLICIT))
    }
    companion object {
        fun supports(model: AiModel, capability: AiCapability) = when(capability) { AiCapability.TOOLS -> model.supportsTools; AiCapability.STREAMING -> model.supportsStreaming; else -> capability in model.inputCapabilities }
        fun compatible(entry: AiModelCatalogEntry, role: AiModelRole?) = when(role) {
            AiModelRole.VISION -> supports(entry.model, AiCapability.IMAGE)
            AiModelRole.AGENTIC -> entry.kind == AiModelKind.CONCRETE && supports(entry.model, AiCapability.TOOLS)
            else -> supports(entry.model, AiCapability.TEXT)
        }
    }
}
