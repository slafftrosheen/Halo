package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ai.ProviderControlRepository
import com.halo.sdk.provider.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Filter/projection work happens off the composition thread. No catalogue fetch on entry. */
enum class ModelCostFilter { ALL, FREE, PAID }

data class ModelBrowserFilter(val query: String = "", val provider: String? = null,
    val location: AiExecutionLocation? = null, val capability: AiCapability? = null, val cost: ModelCostFilter = ModelCostFilter.ALL, val role: AiModelRole? = null)
data class ModelBrowserState(val entries: List<AiModelCatalogEntry> = emptyList(), val refreshing: Boolean = false, val errors: Boolean = false, val candidates: Map<AiModelRef, AiRoleCandidate> = emptyMap())
class ModelBrowserViewModel(val control: ProviderControlRepository) : ViewModel() {
    val filter = MutableStateFlow(ModelBrowserFilter())
    val state = combine(control.catalogue.state, filter, control.router.selectedProviderId, control.router.selectedModelId, control.roleCandidates) { catalogue, f, provider, model, roles ->
        val candidates = roles[f.role ?: AiModelRole.CHAT].orEmpty().associateBy { it.ref }
        val visible = catalogue.models.filter { entry ->
            (f.provider == null || entry.model.providerId == f.provider) &&
                (f.location == null || entry.runtime.location == f.location) &&
                (f.capability == null || f.capability in candidates[entry.runtime.ref]?.effectiveCapabilities.orEmpty()) &&
                (f.cost == ModelCostFilter.ALL || (f.cost == ModelCostFilter.FREE && entry.model.isFree) || (f.cost == ModelCostFilter.PAID && !entry.model.isFree && entry.runtime.location == AiExecutionLocation.REMOTE)) &&
                (f.role == null || candidates[entry.runtime.ref]?.eligible == true ||
                    (entry.runtime.installed == false && f.role in setOf(AiModelRole.CHAT, AiModelRole.FAST, AiModelRole.REASONING))) &&
                (f.query.isBlank() || (entry.model.displayName + " " + entry.model.id + " " + entry.model.providerId).contains(f.query, ignoreCase = true))
        }.sortedBy { if (it.model.providerId == provider && it.model.id == model) 0 else if (it.runtime.location == AiExecutionLocation.ON_DEVICE) 1 else 2 }
        ModelBrowserState(visible, catalogue.refreshingProviders.isNotEmpty(), catalogue.providerErrors.isNotEmpty(), candidates)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelBrowserState())
    private var entered = false
    private var route: Pair<String?, AiModelRole?>? = null
    fun enter(provider: String?, role: AiModelRole?) {
        val next = provider to role
        if (entered && route == next) return
        entered = true; route = next
        filter.value = ModelBrowserFilter(provider = provider, role = role)
    }
    fun pick(entry: AiModelCatalogEntry, onPicked: () -> Unit = {}) {
        if (entry.runtime.installed == false) return
        if (control.rows.value.firstOrNull { it.descriptor.providerId == entry.model.providerId }?.configured != true) return
        val role = filter.value.role
        if (state.value.candidates[entry.runtime.ref]?.eligible != true) return
        viewModelScope.launch(Dispatchers.IO) {
            if (entry.runtime.location == AiExecutionLocation.ON_DEVICE) {
                control.catalogue.refresh(entry.model.providerId)
                val local = control.providers.firstOrNull { it.id == entry.model.providerId } ?: return@launch
                if (local.listModels().none { it.id == entry.model.id }) return@launch
            }
            if (role == null || role == AiModelRole.CHAT) control.router.selectTarget(entry.runtime.ref)
            else control.router.setRoleBinding(AiRoleBinding(role, entry.model.providerId, entry.model.id, AiRoleSelection.EXPLICIT))
            kotlinx.coroutines.withContext(Dispatchers.Main) { onPicked() }
        }
    }
}
