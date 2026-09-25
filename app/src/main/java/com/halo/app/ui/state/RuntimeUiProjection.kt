package com.halo.app.ui.state

import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.interaction.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.runtime.resource.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

data class RuntimeTaskSummary(
    val activeCount: Int,
    val waitingCount: Int,
    val primaryWaitingRun: AgentRun? = null,
    val statusText: String? = null,
)

data class RuntimeResourceUiSummary(
    val activeLeases: List<ResourceLeaseRecord>,
    val waitingRequests: List<ResourceWaitRecord>,
    val activeLabels: List<String>,
)

data class RuntimeCapabilityUiSummary(
    val availableCount: Int,
    val needSetupCount: Int,
    val unavailableCount: Int,
)

class RuntimeUiProjection(
    runStore: AgentRunStore?,
    resourceLeaseManager: ResourceLeaseManager?,
    catalogueSource: CapabilityCatalogueSource?,
    scope: CoroutineScope,
) {

    val taskSummary: StateFlow<RuntimeTaskSummary> = (runStore?.observeRuns() ?: flowOf(emptyList()))
        .map { runs ->
            val nonTerminal = runs.filterNot { it.state.isTerminal }
            val active = nonTerminal.filter { it.state == AgentRunState.EXECUTING || it.state == AgentRunState.PLANNING }
            val waiting = nonTerminal.filter {
                it.state in setOf(
                    AgentRunState.WAITING_FOR_APPROVAL,
                    AgentRunState.WAITING_FOR_CLARIFICATION,
                    AgentRunState.WAITING_FOR_CAPABILITY,
                    AgentRunState.WAITING_FOR_RESOURCE,
                    AgentRunState.SUSPENDED,
                )
            }
            val primaryWaiting = waiting.firstOrNull()
            val text = when (primaryWaiting?.state) {
                AgentRunState.WAITING_FOR_APPROVAL -> "Needs your approval"
                AgentRunState.WAITING_FOR_CLARIFICATION -> "Needs your answer"
                AgentRunState.WAITING_FOR_RESOURCE -> "Waiting for resource"
                AgentRunState.WAITING_FOR_CAPABILITY -> "Waiting for device"
                AgentRunState.SUSPENDED -> "Task paused"
                else -> null
            }
            RuntimeTaskSummary(
                activeCount = active.size,
                waitingCount = waiting.size,
                primaryWaitingRun = primaryWaiting,
                statusText = text,
            )
        }.stateIn(scope, SharingStarted.Eagerly, RuntimeTaskSummary(0, 0))

    val resourceSummary: StateFlow<RuntimeResourceUiSummary> = (resourceLeaseManager?.snapshotState ?: flowOf(ResourceLeaseSnapshot(emptyList(), emptyList())))
        .map { snapshot ->
            val labels = snapshot.active.flatMap { lease ->
                lease.claims.map { RuntimeUiFormatters.formatResource(it.key.resourceId) }
            }.distinct()
            RuntimeResourceUiSummary(
                activeLeases = snapshot.active,
                waitingRequests = snapshot.waiting,
                activeLabels = labels,
            )
        }.stateIn(scope, SharingStarted.Eagerly, RuntimeResourceUiSummary(emptyList(), emptyList(), emptyList()))

    val capabilitySummary: StateFlow<RuntimeCapabilityUiSummary> = (catalogueSource?.snapshot ?: flowOf(CapabilityCatalogueSnapshot(emptyList(), 0L)))
        .map { snapshot ->
            val avail = snapshot.capabilities.count { it.availability == CapabilityAvailability.AVAILABLE }
            val setup = snapshot.capabilities.count { it.availability == CapabilityAvailability.NEED_SETUP }
            val unavail = snapshot.capabilities.count { it.availability == CapabilityAvailability.UNAVAILABLE || it.availability == CapabilityAvailability.BLOCKED }
            RuntimeCapabilityUiSummary(
                availableCount = avail,
                needSetupCount = setup,
                unavailableCount = unavail,
            )
        }.stateIn(scope, SharingStarted.Eagerly, RuntimeCapabilityUiSummary(0, 0, 0))
}
