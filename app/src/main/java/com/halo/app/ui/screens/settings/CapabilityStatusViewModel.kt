package com.halo.app.ui.screens.settings

import com.halo.app.data.memory.RoomMemoryRetriever
import com.halo.app.mcp.McpIntegrationManager
import com.halo.runtime.cognition.procedures.LearnedProcedureStore
import com.halo.sdk.runtime.thermal.ThermalStateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MINING-P1-001 capability status summary.
 *
 * One concise read-only overview of what Halo can currently do. It is a summary on purpose: deep
 * configuration stays on the dedicated screens, and nothing here starts work (no connection attempt,
 * no model probe, no memory scan beyond a counted query).
 *
 * Wording is deliberately conservative. A capability that is not installed says so; a feature that is
 * source-complete but intentionally inert (streaming speech) reports "Off" rather than "Ready".
 */
data class CapabilityStatusUiState(
    val loading: Boolean = true,
    val agentRunDeadline: String = "Enabled",
    val toolLoopGuard: String = "Enabled",
    val thermalPolicy: String = "Unknown",
    val durableFacts: String = "—",
    val memorySearch: String = "Lexical",
    val semanticSearch: String = "Not installed",
    val learnedProcedures: String = "—",
    val mcpServers: String = "—",
    val mcpTools: String = "—",
    val bargeIn: String = "Standard",
    val semanticInterrupt: String = "Not installed",
    val streamingSpeech: String = "Off",
    val replyableNotifications: String = "—",
    /**
     * MINING-P2-001 optional capability models. Rendered even when nothing is installed, so the user
     * can see what Halo could gain instead of a mystery list of filenames.
     */
    val capabilityModels: List<Pair<String, String>> = emptyList(),
)

class CapabilityStatusViewModel(
    private val scope: CoroutineScope,
    private val thermalSource: ThermalStateSource?,
    private val memoryRetriever: RoomMemoryRetriever?,
    private val procedureStore: LearnedProcedureStore?,
    private val mcpStatus: suspend () -> McpIntegrationManager.McpStatusSummary,
    private val replyableCount: () -> Int,
    private val capabilityPacks: suspend () -> List<com.halo.sdk.runtime.capability.RuntimeCapabilityStatus> = { emptyList() },
) {

    private val _state = MutableStateFlow(CapabilityStatusUiState())
    val state: StateFlow<CapabilityStatusUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** User-initiated refresh. Never called automatically by a background loop. */
    fun refresh() {
        scope.launch {
            val thermal = thermalSource?.state?.value
            val facts = runCatching { memoryRetriever?.totalCount() }.getOrNull()
            val semantic = runCatching { memoryRetriever?.availability() }.getOrNull()
            val procedures = runCatching { procedureStore?.all()?.size }.getOrNull()
            val mcp = runCatching { mcpStatus() }.getOrNull()
            val packs = capabilityPacks()
            _state.value = CapabilityStatusUiState(
                loading = false,
                // Bounded-run guard is wired into both the provider loop and the tool loop.
                agentRunDeadline = "Enabled",
                toolLoopGuard = "Enabled",
                thermalPolicy = thermal?.severity?.label ?: "Unknown",
                durableFacts = facts?.toString() ?: "—",
                memorySearch = "Lexical",
                semanticSearch = when (semantic?.name) {
                    "AVAILABLE" -> "Available"
                    "LOADING" -> "Loading"
                    else -> "Not installed"
                },
                learnedProcedures = procedures?.toString() ?: "—",
                mcpServers = mcp?.let { "${it.connected} connected / ${it.configured} configured" } ?: "—",
                mcpTools = mcp?.enabledToolCount?.toString() ?: "—",
                bargeIn = "Standard",
                // No classifier artifact is installed in this batch, and Halo never pretends one is.
                semanticInterrupt = "Not installed",
                // Source exists, but terminal-stream identity is not yet proven for every provider
                // path, so the feature stays off until the stabilization pass enables it.
                streamingSpeech = "Off",
                replyableNotifications = replyableCount().toString(),
                capabilityModels = if (packs.isNotEmpty()) packs.map {
                    it.descriptor.displayName to it.state.name.lowercase().replace('_', ' ')
                } else com.halo.sdk.runtime.capability.BuiltInOptionalCapabilities.defaults()
                    .map { capability -> capability.displayName to capability.stateLabel },
            )
        }
    }
}
