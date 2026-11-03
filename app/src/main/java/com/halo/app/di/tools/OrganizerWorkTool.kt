package com.halo.app.di.tools

import com.halo.app.everyday.organizer.*
import com.halo.runtime.cognition.routine.AutomationPermission
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

/** Internal bridge: only the existing routine runner can supply a durable claimed run scope. */
class OrganizerWorkTool(private val owner: () -> OrganizerOperations, private val canRun: () -> Boolean) : ToolExecutor {
    private val definition = ToolDefinition(OrganizerOperations.PERFORM, OrganizerOperations.PERFORM,
        "Execute one exact reviewed Organizer capability step under an existing automation journal claim.",
        EverydayToolContracts.obj(mapOf("id" to EverydayToolContracts.text(200), "planId" to EverydayToolContracts.text(80),
            "index" to EverydayToolContracts.number(0, 5)), listOf("id", "planId", "index")))
    override fun definitions() = listOf(definition)
    fun descriptor() = ToolDescriptor(ToolId(definition.name), definition, ToolCategory.AUTOMATION,
        visibility = ToolVisibility.INTERNAL,
        security = ToolSecurityPolicy(ToolRiskClass.REVERSIBLE, ConfirmationPolicy.NONE, ReplaySafety.NEVER_REPLAY),
        execution = ToolExecutionPolicy(timeoutMs = 30000, maxOutputChars = 2000),
        automation = ToolAutomationMetadata(AutomationPermission.ALLOWED_WITH_ROUTINE_GRANT, true, true, false),
        operability = ToolOperability(capabilityId = CapabilityId("workspace.write"), owner = "Organizer / RoutineExecutor",
            operation = ToolOperation.EDIT, resultEncoding = ToolResultEncoding.JSON,
            resultSchema = EverydayToolContracts.obj(mapOf("id" to EverydayToolContracts.text(200), "version" to EverydayToolContracts.number(1, Long.MAX_VALUE),
                "state" to EverydayToolContracts.choice(HaloWorkState.entries.map { it.name }), "saved" to mapOf("type" to "boolean")), listOf("id", "version", "state", "saved")),
            sourceVersionArguments = setOf("planId"), resultIdFields = setOf("id")))
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        var entered = false
        try {
            require(toolCall.functionName == definition.name && canRun()) { "Organizer automation is not currently permitted" }
            EverydayToolContracts.validate(toolCall.arguments, definition.parameters)
            val scope = requireNotNull(coroutineContext[RunToolScope]) { "A claimed automation run is required" }
            entered = true
            val saved = com.halo.app.everyday.EverydayWriteAuthority.run({ check(canRun()) }) {
                owner().perform(toolCall.arguments.getValue("id") as String, toolCall.arguments.getValue("planId") as String,
                    (toolCall.arguments.getValue("index") as Number).toInt(), scope.runId.value, toolCall.id)
            }
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, JSONObject().put("id", saved.id).put("version", saved.version)
                .put("state", saved.commitment.state.name).put("saved", true).toString())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            return if (entered) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
                "Inspect the exact Organizer execution before continuing. Never replay a claimed step.", completionUnknown = true, code = "ORGANIZER_WORK_UNCERTAIN")
            else ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "A permitted claimed Organizer automation is required.", ToolRejectionKind.PERMISSION_REQUIRED)
        }
    }
}
