package com.halo.app.di.tools

import com.halo.runtime.cognition.procedures.LearnedProcedure
import com.halo.runtime.cognition.procedures.LearnedProcedureStore
import com.halo.runtime.cognition.procedures.ProcedureOrigin
import com.halo.runtime.cognition.procedures.ProcedureProposal
import com.halo.runtime.cognition.procedures.ProcedureStep
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import org.json.JSONArray
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MINING-P1-001 learned procedures.
 *
 * Three tools, no new execution machinery:
 *
 * - `halo_propose_procedure` — the model proposes; nothing is persisted. Unknown capabilities are
 *   rejected in the proposal rather than stored and failed later.
 * - `halo_save_procedure` — `ConfirmationPolicy.ALWAYS`, so Halo's ordinary durable approval surface
 *   is what the user answers. Persistence happens only after that approval.
 * - `halo_use_procedure` — returns the recipe as instructions and records usage. It does NOT execute
 *   steps: each step still goes through normal tool policy, approval and the action journal, so a
 *   procedure saved while a capability was allowed can never bypass a later policy change.
 */
class ProcedureToolExecutor(
    private val store: LearnedProcedureStore,
    /**
     * Used only to confirm that a proposed capability exists today. A lambda rather than a
     * [ToolRegistry] reference because this executor is constructed *by* the registry; the lookup is
     * deferred to execution time so the composition root stays acyclic.
     */
    private val capabilityKnown: (String) -> Boolean,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis,
) : ToolExecutor {

    /** Pending proposals are process-memory only: an unconfirmed proposal never becomes durable. */
    private val pending = LinkedHashMap<String, ProcedureProposal>()
    private val saveMutex = Mutex()

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_propose_procedure",
            name = "halo_propose_procedure",
            description = "Propose a reusable multi-step procedure built from existing Halo actions. " +
                "Nothing is saved until the user confirms.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string", "maxLength" to ProcedureProposal.MAX_NAME_CHARS),
                    "description" to mapOf("type" to "string", "maxLength" to ProcedureProposal.MAX_DESCRIPTION_CHARS),
                    "triggers" to mapOf(
                        "type" to "array",
                        "items" to mapOf("type" to "string", "maxLength" to ProcedureProposal.MAX_TRIGGER_CHARS),
                        "maxItems" to ProcedureProposal.MAX_TRIGGERS,
                    ),
                    "steps" to mapOf(
                        "type" to "array",
                        "minItems" to 1,
                        "maxItems" to ProcedureProposal.MAX_STEPS,
                        "items" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "capabilityId" to mapOf("type" to "string", "maxLength" to 120),
                                "intent" to mapOf("type" to "string", "maxLength" to 300),
                            ),
                            "required" to listOf("capabilityId"),
                            "additionalProperties" to false,
                        ),
                    ),
                ),
                "required" to listOf("name", "steps"),
                "additionalProperties" to false,
            ),
        ),
        ToolDefinition(
            id = "halo_save_procedure",
            name = "halo_save_procedure",
            description = "Save a proposed procedure after the user confirms it.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("proposalId" to mapOf("type" to "string", "maxLength" to 64)),
                "required" to listOf("proposalId"),
                "additionalProperties" to false,
            ),
        ),
        ToolDefinition(
            id = "halo_use_procedure",
            name = "halo_use_procedure",
            description = "Read a saved procedure by either its exact procedureId or one unique full name. Returns guidance only; no step is executed, scheduled or authorized. Missing capabilities reject. Each actual step still requires normal actions, current policy and approval.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "procedureId" to mapOf("type" to "string", "maxLength" to 64),
                    "name" to mapOf("type" to "string", "maxLength" to 120),
                ),
                "required" to emptyList<String>(),
                "additionalProperties" to false,
            ),
        ),
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: com.halo.runtime.cognition.tools.ToolRejectionKind = com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        return when (toolCall.functionName) {
            "halo_propose_procedure" -> propose(toolCall)
            "halo_save_procedure" -> save(toolCall)
            "halo_use_procedure" -> use(toolCall)
            else -> reject("Unsupported procedure tool: ${toolCall.functionName}", com.halo.runtime.cognition.tools.ToolRejectionKind.TOOL_UNKNOWN)
        }
    }

    private fun propose(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: com.halo.runtime.cognition.tools.ToolRejectionKind = com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        if (toolCall.arguments.keys.any { it !in setOf("name", "description", "triggers", "steps") }) {
            return reject("Invalid arguments")
        }
        val name = toolCall.arguments["name"]?.toString()?.trim().orEmpty()
        if (name.isBlank() || name.length > ProcedureProposal.MAX_NAME_CHARS) return reject("A procedure needs a name")
        val description = toolCall.arguments["description"]?.toString()?.trim().orEmpty()
            .take(ProcedureProposal.MAX_DESCRIPTION_CHARS)
        val triggers = parseTriggers(toolCall.arguments["triggers"]) ?: return reject("Triggers must be short phrases")
        val steps = parseSteps(toolCall.arguments["steps"]) ?: return reject("Steps must list capabilityId entries")
        if (steps.isEmpty()) return reject("A procedure needs at least one step")
        if (steps.size > ProcedureProposal.MAX_STEPS) return reject("A procedure may have at most ${ProcedureProposal.MAX_STEPS} steps")

        /*
         * MINING-P1-001: capability truth is checked at proposal time. A procedure cannot name an
         * action Halo does not have, and it cannot invent authority for one it does.
         */
        val unknown = steps.map { it.capabilityId }
            .distinct()
            .filter { capability -> !capabilityKnown(capability) }
        if (unknown.isNotEmpty()) {
            return reject(
                "Unknown capabilities: ${unknown.take(5).joinToString(", ")}. " +
                    "Only existing actions can be used.",
                com.halo.runtime.cognition.tools.ToolRejectionKind.CAPABILITY_UNAVAILABLE
            )
        }

        val proposal = ProcedureProposal(
            proposalId = newId(),
            name = name,
            description = description,
            triggerPhrases = triggers,
            steps = steps,
            unknownCapabilities = emptyList(),
            createdAt = now(),
        )
        synchronized(pending) {
            pending.entries.removeAll { now() - it.value.createdAt > 10 * 60_000L }
            while (pending.size >= 32) pending.remove(pending.keys.first())
            pending[proposal.proposalId] = proposal
        }
        return ToolExecutionResult.Success(
            toolCall.id,
            toolCall.functionName,
            buildString {
                append("PROPOSED proposal=").append(proposal.proposalId).append('\n')
                append("name: ").append(proposal.name).append('\n')
                if (proposal.description.isNotBlank()) append("description: ").append(proposal.description).append('\n')
                append("steps:\n")
                proposal.steps.forEachIndexed { index, step ->
                    append("  ").append(index + 1).append(". ").append(step.capabilityId)
                    if (step.intent.isNotBlank()) append(" — ").append(step.intent)
                    append('\n')
                }
                append("WAITING_FOR_USER_CONFIRMATION: call halo_save_procedure only after the user agrees.")
            },
        )
    }

    private suspend fun save(toolCall: ToolCall): ToolExecutionResult = saveMutex.withLock { saveOnce(toolCall) }

    private suspend fun saveOnce(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: com.halo.runtime.cognition.tools.ToolRejectionKind = com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        if (toolCall.arguments.keys != setOf("proposalId")) return reject("Invalid arguments")
        val proposalId = toolCall.arguments["proposalId"]?.toString()?.trim().orEmpty()
        val proposal = synchronized(pending) { pending[proposalId] } ?: return reject("Unknown or expired proposal", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
        if (now() - proposal.createdAt !in 0..10 * 60_000L) return reject("Unknown or expired proposal", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
        if (!proposal.savable) return reject("That proposal cannot be saved", com.halo.runtime.cognition.tools.ToolRejectionKind.UNSUPPORTED_OPERATION)
        if (proposal.steps.any { !capabilityKnown(it.capabilityId) }) return reject(
            "A proposed action is no longer registered. Review a new proposal before saving.",
            com.halo.runtime.cognition.tools.ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        val timestamp = now()
        val procedure = LearnedProcedure(
            id = proposal.proposalId,
            name = proposal.name,
            description = proposal.description,
            triggerPhrases = proposal.triggerPhrases,
            steps = proposal.steps,
            origin = ProcedureOrigin.AGENT_PROPOSED,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        store.upsert(procedure)
        synchronized(pending) { pending.remove(proposalId) }
        return ToolExecutionResult.Success(
            toolCall.id,
            toolCall.functionName,
            "SAVED procedure=${procedure.id} name=\"${procedure.name}\" steps=${procedure.steps.size}",
        )
    }

    private suspend fun use(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: com.halo.runtime.cognition.tools.ToolRejectionKind = com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        if (toolCall.arguments.keys.any { it !in setOf("procedureId", "name") }) return reject("Invalid arguments")
        if (toolCall.arguments.values.any { it !is String }) return reject("Invalid arguments")
        val id = toolCall.arguments["procedureId"]?.toString()?.trim().orEmpty()
        val name = toolCall.arguments["name"]?.toString()?.trim().orEmpty()
        if (id.isBlank() == name.isBlank()) return reject("Choose either an exact procedure ID or one unique full name")
        val procedures = store.all()
        val procedure = when {
            id.isNotBlank() -> procedures.singleOrNull { it.id == id }
            name.isNotBlank() -> procedures.singleOrNull { it.name.equals(name, ignoreCase = true) }
            else -> null
        } ?: return reject("No matching procedure", com.halo.runtime.cognition.tools.ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        if (!procedure.enabled) return reject("That procedure is disabled", com.halo.runtime.cognition.tools.ToolRejectionKind.TOOL_DISABLED)
        if (procedure.steps.size !in 1..ProcedureProposal.MAX_STEPS || procedure.steps.any { !capabilityKnown(it.capabilityId) })
            return reject("The saved recipe needs review because an action is missing or its step limit changed.",
                com.halo.runtime.cognition.tools.ToolRejectionKind.CAPABILITY_UNAVAILABLE)

        store.recordUsage(procedure.id, now())
        return ToolExecutionResult.Success(
            toolCall.id,
            toolCall.functionName,
            buildString {
                append("PROCEDURE ").append(procedure.name).append(" (follow these steps with normal actions)\n")
                procedure.steps.forEachIndexed { index, step ->
                    append(index + 1).append(". ").append(step.capabilityId)
                    if (step.intent.isNotBlank()) append(" — ").append(step.intent)
                    append('\n')
                }
                append("Each step still requires its own permission; if an action is not available, stop and say so.")
            },
        )
    }

    private fun parseTriggers(raw: Any?): List<String>? {
        if (raw == null) return emptyList()
        val values = when (raw) {
            is JSONArray -> (0 until raw.length()).mapNotNull { raw.optString(it).takeIf { value -> value.isNotBlank() } }
            is List<*> -> raw.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            else -> return null
        }
        if (values.size > ProcedureProposal.MAX_TRIGGERS) return null
        return values.map { it.take(ProcedureProposal.MAX_TRIGGER_CHARS) }
    }

    private fun parseSteps(raw: Any?): List<ProcedureStep>? {
        val items = when (raw) {
            is JSONArray -> (0 until raw.length()).map { raw.optJSONObject(it) }
            is List<*> -> raw.map { item ->
                when (item) {
                    is Map<*, *> -> org.json.JSONObject(item)
                    is org.json.JSONObject -> item
                    else -> null
                }
            }
            else -> return null
        }
        if (items.any { it == null }) return null
        return items.filterNotNull().map { item ->
            val capability = item.optString("capabilityId").trim()
            if (capability.isEmpty()) return null
            ProcedureStep(
                capabilityId = capability.take(120),
                intent = item.optString("intent").take(300),
            )
        }
    }
}
