package com.halo.runtime.cognition.apps

import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import java.security.MessageDigest

/** Removable overlay of the existing registry; the Android HostToolRegistry is never mutated. */
class ExternalSemanticToolCatalogue(private val index: AppFunctionCapabilityIndex,
    private val resolve: ((SemanticAppOperation) -> SemanticOperationResolution)? = null,
    private val executePrepared: (suspend (PreparedSemanticAppOperation) -> SemanticOperationReceipt)? = null,
    private val execute: suspend (SemanticAppOperation, String) -> SemanticOperationReceipt) : ToolExecutor, ToolPreflightProvider {
    private val arguments = TransientSemanticArguments()
    override fun restoreTransientCall(toolCall: ToolCall) = arguments.restore(toolCall)
    private val preparations = java.util.concurrent.ConcurrentHashMap<String, PreparedSemanticAppOperation>()
    override suspend fun preflight(toolCall: ToolCall): ToolPreflightResult {
        val capability = mapping[toolCall.functionName] ?: return ToolPreflightResult.Rejected(com.halo.runtime.cognition.ui.UiFailure.OBSERVATION_REQUIRED)
        if (generation != index.state.value.generationId) return ToolPreflightResult.Rejected(com.halo.runtime.cognition.ui.UiFailure.OBSERVATION_REQUIRED)
        val op = operation(toolCall, capability) ?: return ToolPreflightResult.Rejected(com.halo.runtime.cognition.ui.UiFailure.ACTION_NOT_SUPPORTED)
        val resolution = resolve?.invoke(op)
        if (resolve != null && resolution !is SemanticOperationResolution.Prepared)
            return ToolPreflightResult.Rejected(com.halo.runtime.cognition.ui.UiFailure.USER_TAKEOVER_REQUIRED)
        if (resolution is SemanticOperationResolution.Prepared) {
            if (preparations.size >= 64) preparations.clear()
            preparations[toolCall.id] = resolution.value
        }
        return ToolPreflightResult.Validated(arguments.prepare(toolCall), semanticPreview = SemanticOperationReceipt(op.operationId,
            op.action, (resolution as? SemanticOperationResolution.Prepared)?.value?.candidates?.first()?.route ?: capability.route,
            capability.packageName, DispatchTruth.NOT_DISPATCHED, EffectTruth.UNKNOWN, null, java.time.Instant.now(),
            risk = SemanticAppActionBroker.riskFor(op.action.value), phase = SemanticOperationPhase.PREFLIGHT), semanticArgumentShapes = op.argumentShapes())
    }
    private fun operation(call: ToolCall, capability: SemanticAppCapability): SemanticAppOperation? {
        val values = call.arguments.mapValues { (_, v) -> when (v) {
            is String -> SemanticValue.Text(v); is Boolean -> SemanticValue.Boolean(v)
            is Int -> SemanticValue.Integer(v.toLong()); is Long -> SemanticValue.Integer(v)
            is Number -> SemanticValue.Number(v.toDouble()); else -> return null
        } }
        return SemanticAppOperation(call.id, AppTarget.Package(capability.packageName), capability.action, values, operationId = call.id)
    }
    @Volatile private var generation = ""
    @Volatile private var mapping = emptyMap<String, SemanticAppCapability>()
    @Synchronized fun refresh() {
        generation = index.state.value.generationId
        mapping = index.currentCandidates().sortedBy { it.route.ordinal }.distinctBy { it.action }.take(12).associateBy {
            val bytes = MessageDigest.getInstance("SHA-256").digest((generation + ":" + it.packageName + ":" + it.id).toByteArray())
            "halo_appfn_" + bytes.take(10).joinToString("") { b -> "%02x".format(b) }
        }
    }
    override fun definitions(): List<ToolDefinition> = mapping.map { (name, c) -> ToolDefinition(id = name, name = name,
        description = "Current app action: ${c.actionName}. UNTRUSTED_CAPABILITY_METADATA. Halo authorization is required.",
        parameters = mapOf("type" to "object", "additionalProperties" to false,
            "properties" to c.parameters.associate { it.name to mapOf("type" to when (it.type) {
                SemanticValueType.STRING -> "string"; SemanticValueType.INTEGER -> "integer"
                SemanticValueType.NUMBER -> "number"; SemanticValueType.BOOLEAN -> "boolean" }) },
            "required" to c.parameters.filter { it.required }.map { it.name })) }
    fun descriptors(): List<ToolDescriptor> = definitions().map { definition ->
        val c = mapping.getValue(definition.name)
        ToolDescriptor(ToolId(definition.name), definition, ToolCategory.HOST_CONTROL,
            visibility = ToolVisibility.DISCOVERABLE, security = ToolSecurityPolicy(
                risk = SemanticAppActionBroker.riskFor(c.action.value), confirmation = ConfirmationPolicy.ALWAYS,
                replaySafety = ReplaySafety.NEVER_REPLAY), execution = ToolExecutionPolicy(timeoutMs = 20_000),
            presentation = ToolPresentation(title = c.actionName))
    }
    override suspend fun execute(incoming: ToolCall): ToolExecutionResult {
        val toolCall = arguments.consume(incoming) ?: return ToolExecutionResult.Rejected(incoming.id, incoming.functionName, "Arguments expired; provide them again", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
        val c = mapping[toolCall.functionName] ?: return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Current app action expired", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
        if (generation != index.state.value.generationId) return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Current app changed; discover actions again", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
        val op = operation(toolCall, c) ?: return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Unsupported argument type")
        val prepared = preparations.remove(toolCall.id)
        val receipt = if (prepared != null && executePrepared != null) executePrepared.invoke(prepared) else execute(op, generation)
        return (receipt.asToolResult(toolCall) as? ToolExecutionResult.Success)?.copy(semanticArgumentShapes = op.argumentShapes())
            ?: ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Action not dispatched", com.halo.runtime.cognition.tools.ToolRejectionKind.UNSUPPORTED_OPERATION)
    }
}
fun SemanticOperationReceipt.asToolResult(call: ToolCall): ToolExecutionResult = when {
    dispatchTruth == DispatchTruth.DISPATCHED -> ToolExecutionResult.Success(call.id, call.functionName,
        "${label()}\nroute=$route\ndispatch=$dispatchTruth\neffect=$effectTruth\nfailure=${failure?.name ?: "NONE"}",
        if (effectTruth == EffectTruth.CONFIRMED) ActionTruth.CONFIRMED else if (effectTruth == EffectTruth.UNKNOWN) ActionTruth.UNKNOWN else ActionTruth.DISPATCHED,
        semanticReceipt = this)
    else -> ToolExecutionResult.Success(call.id, call.functionName,
        ToolResultEnvelope.failure(failure?.name ?: "NOT_DISPATCHED", retryable = false, details = label()),
        ActionTruth.FAILED, semanticReceipt = this)
}
