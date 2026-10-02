package com.halo.app.di.tools.host

import com.halo.host.android.accessibility.HaloAccessibilityService
import com.halo.runtime.cognition.apps.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.UiInspectability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only fresh, unambiguous, exact semantic targets can become fallback capabilities. */
class SemanticAccessibilityAdapter(private val tools: AccessibilityTools) : SemanticRouteExecutor {
    private val calls = java.util.concurrent.ConcurrentHashMap<String, ToolCall>()
    fun capabilities(operation: SemanticAppOperation): List<SemanticAppCapability> {
        val service = HaloAccessibilityService.instance ?: return emptyList()
        val snapshot = service.latestSemanticSnapshot ?: return emptyList()
        if (snapshot.uiRevision != service.currentActionableRevision || System.currentTimeMillis() - snapshot.capturedAtMs !in 0..30_000 ||
            snapshot.inspectability != UiInspectability.INSPECTABLE || ManualTakeoverPolicy.blocked(snapshot.packageName, operation.action.value)) return emptyList()
        val pkg = snapshot.packageName ?: return emptyList()
        val call = if (operation.action.value == "navigate_back") ToolCall(operation.operationId, "halo_ui_global_action",
            mapOf("snapshotId" to snapshot.snapshotId, "action" to "back")) else {
            val targets = snapshot.nodes.filter { !it.sensitive && it.enabled && it.clickable &&
                SemanticActionMapping.fromIdentifier(it.text ?: it.contentDescription ?: "") == operation.action }
            val node = targets.singleOrNull() ?: return emptyList()
            ToolCall(operation.operationId, "halo_ui_click", mapOf("snapshotId" to snapshot.snapshotId, "token" to (node.token ?: return emptyList()), "expectedEffect" to "NONE"))
        }
        val id = snapshot.snapshotId + ":" + operation.action.value
        calls.entries.removeIf { it.key.substringBefore(':') != snapshot.snapshotId }
        calls[id] = call
        return listOf(SemanticAppCapability(id, pkg, operation.action, operation.action.value.replace('_', ' '), null,
            emptyList(), AppOperationRoute.ACCESSIBILITY, packageRevision = snapshot.uiRevision))
    }
    override suspend fun execute(operation: SemanticAppOperation, capability: SemanticAppCapability, markDispatched: () -> Unit): SemanticRouteOutcome = withContext(Dispatchers.Main.immediate) {
        val call = calls.remove(capability.id) ?: return@withContext SemanticRouteOutcome(DispatchTruth.NOT_DISPATCHED, EffectTruth.UNKNOWN, SemanticOperationFailure.STALE_GENERATION)
        if (tools.preflight(call) is ToolPreflightResult.Rejected) return@withContext SemanticRouteOutcome(DispatchTruth.NOT_DISPATCHED, EffectTruth.UNKNOWN, SemanticOperationFailure.STALE_GENERATION)
        markDispatched()
        val result = tools.execute(call)
        val receipt = (result as? ToolExecutionResult.Success)?.uiReceipt
        SemanticRouteOutcome(DispatchTruth.DISPATCHED, when (receipt?.actionTruth) {
            com.halo.runtime.cognition.agent.ActionTruth.CONFIRMED -> EffectTruth.CONFIRMED
            com.halo.runtime.cognition.agent.ActionTruth.DISPATCHED -> EffectTruth.UNCONFIRMED
            else -> EffectTruth.UNKNOWN
        }, if (receipt?.failureCode != null || result !is ToolExecutionResult.Success) SemanticOperationFailure.UNKNOWN else null)
    }
}
