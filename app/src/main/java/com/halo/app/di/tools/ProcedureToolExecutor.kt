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
import org.json.JSONObject
import com.halo.app.data.procedure.ProcedureContracts
import com.halo.app.data.procedure.ProcedureReview
import com.halo.runtime.cognition.tools.ToolDescriptor
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MINING-P1-001 learned procedures.
 *
 * Reviewed templates and management tools, no new execution machinery:
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
    private val descriptorFor: (String) -> ToolDescriptor? = { null },
    private val validateCall: (ToolCall) -> ToolExecutionResult.Rejected? = {
        ToolExecutionResult.Rejected(it.id, it.functionName, "Typed workflow validation unavailable")
    },
    private val reviewLanguage: () -> String = { "en" },
    private val reviewText: (String) -> String = { it },
    private val validateTemplate: (ToolCall, Map<String, String>) -> ToolExecutionResult.Rejected? = { call, _ ->
        ToolExecutionResult.Rejected(call.id, call.functionName, "Typed workflow template validation unavailable")
    },
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
                    "inputs" to mapOf("type" to "array", "maxItems" to ProcedureContracts.MAX_INPUTS,
                        "items" to mapOf("type" to "object", "properties" to mapOf(
                            "name" to mapOf("type" to "string", "maxLength" to 40),
                            "description" to mapOf("type" to "string", "maxLength" to 160),
                            "valueType" to mapOf("type" to "string", "enum" to listOf("string", "integer", "boolean"))),
                            "required" to listOf("name", "description"), "additionalProperties" to false)),
                    "steps" to mapOf(
                        "type" to "array",
                        "minItems" to 1,
                        "maxItems" to ProcedureProposal.MAX_STEPS,
                        "items" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "capabilityId" to mapOf("type" to "string", "maxLength" to 120),
                                "intent" to mapOf("type" to "string", "maxLength" to 300),
                                "arguments" to mapOf("type" to "object", "description" to "Reviewed literal arguments. Named inputs bind whole argument values through inputBindings."),
                                "inputBindings" to mapOf("type" to "object", "description" to "Argument name to named text input. No string interpolation; supply current object IDs and revisions on each use."),
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
                "properties" to mapOf("proposalId" to mapOf("type" to "string", "maxLength" to 64),
                    "reviewSummary" to mapOf("type" to "string", "maxLength" to ProcedureReview.MAX_CHARS,
                        "description" to "Exact complete human-readable reviewSummary returned by proposal; mandatory for typed templates and displayed in the approval.")),
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
                    "sourceVersion" to mapOf("type" to "string", "maxLength" to 64, "description" to "Exact current workflow fingerprint from halo_procedures_list; required for typed templates."),
                    "inputs" to mapOf("type" to "object", "description" to "Exact named text inputs required by the saved workflow. No extra inputs."),
                ),
                "required" to emptyList<String>(),
                "additionalProperties" to false,
            ),
        ),
        ToolDefinition("halo_procedures_list", "halo_procedures_list", "List up to three saved reviewed workflows per bounded page and their exact sourceVersion. Follow nextOffset until null. Does not execute or schedule work.",
            mapOf("type" to "object", "properties" to mapOf("offset" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 50)), "required" to emptyList<String>(), "additionalProperties" to false)),
        ToolDefinition("halo_procedure_set_enabled", "halo_procedure_set_enabled", "Enable or disable one exact saved workflow after approval. Does not execute steps or grant unattended authority.",
            mapOf("type" to "object", "properties" to mapOf("procedureId" to mapOf("type" to "string", "maxLength" to 64),
                "sourceVersion" to mapOf("type" to "string", "maxLength" to 64), "enabled" to mapOf("type" to "boolean")),
                "required" to listOf("procedureId", "sourceVersion", "enabled"), "additionalProperties" to false)),
        ToolDefinition("halo_procedure_delete", "halo_procedure_delete", "Delete one exact saved workflow after approval. Existing user files and prior run receipts remain intact.",
            mapOf("type" to "object", "properties" to mapOf("procedureId" to mapOf("type" to "string", "maxLength" to 64),
                "sourceVersion" to mapOf("type" to "string", "maxLength" to 64)),
                "required" to listOf("procedureId", "sourceVersion"), "additionalProperties" to false)),
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: com.halo.runtime.cognition.tools.ToolRejectionKind = com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        return when (toolCall.functionName) {
            "halo_propose_procedure" -> propose(toolCall)
            "halo_save_procedure" -> save(toolCall)
            "halo_use_procedure" -> use(toolCall)
            "halo_procedures_list" -> list(toolCall)
            "halo_procedure_set_enabled", "halo_procedure_delete" -> change(toolCall)
            else -> reject("Unsupported procedure tool: ${toolCall.functionName}", com.halo.runtime.cognition.tools.ToolRejectionKind.TOOL_UNKNOWN)
        }
    }

    private fun propose(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: com.halo.runtime.cognition.tools.ToolRejectionKind = com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        if (toolCall.arguments.keys.any { it !in setOf("name", "description", "triggers", "steps", "inputs") }) {
            return reject("Invalid arguments")
        }
        val name = (toolCall.arguments["name"] as? String)?.trim().orEmpty()
        if (name.isBlank() || name.length > ProcedureProposal.MAX_NAME_CHARS) return reject("A procedure needs a name")
        if (toolCall.arguments.containsKey("description") && toolCall.arguments["description"] !is String) return reject("Description must be text")
        val description = (toolCall.arguments["description"] as? String)?.trim().orEmpty()
        if (description.length > ProcedureProposal.MAX_DESCRIPTION_CHARS) return reject("Description too long")
        val triggers = parseTriggers(toolCall.arguments["triggers"]) ?: return reject("Triggers must be short phrases")
        val steps = parseSteps(toolCall.arguments["steps"]) ?: return reject("Steps must list capabilityId entries")
        val inputs = try { ProcedureContracts.inputs(toolCall.arguments["inputs"]) } catch (_: IllegalArgumentException) { return reject("Invalid named inputs") }
            catch (_: org.json.JSONException) { return reject("Invalid named inputs") }
        if (steps.flatMap { it.inputBindings.values }.any { bound -> inputs.none { it.name == bound } })
            return reject("A step binds an unknown input")
        if (inputs.any { input -> steps.none { input.name in it.inputBindings.values } }) return reject("Every input must be used by a typed step")
        if (steps.isEmpty()) return reject("A procedure needs at least one step")
        if (steps.size > ProcedureProposal.MAX_STEPS) return reject("A procedure may have at most ${ProcedureProposal.MAX_STEPS} steps")
        if (steps.sumOf { (it.argumentsJson?.length ?: 0) + it.intent.length + it.capabilityId.length + it.inputBindings.toString().length } > 8_000)
            return reject("Workflow template exceeds the bounded review size")
        if (steps.any { it.capabilityId in com.halo.runtime.cognition.tools.PersonalConfigurationToolAccess.procedures })
            return reject("A workflow cannot recursively propose, read or manage workflows")

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

        for (step in steps) {
            val literals = ProcedureContracts.literals(step) ?: continue
            if (validateTemplate(ToolCall("workflow-proposal", step.capabilityId, literals),
                    ProcedureContracts.inputTypes(step, inputs)) != null)
                return reject("Workflow literals or named input types do not match the action schema. Review the step before saving.")
        }

        val review = ProcedureReview.summary(name, description, triggers, inputs, steps, reviewLanguage(), reviewText, descriptorFor)
        if (review.length > ProcedureReview.MAX_CHARS) return reject("Workflow too large for a complete approval review. Split it into smaller workflows.")
        val proposal = ProcedureProposal(
            proposalId = newId(),
            name = name,
            description = description,
            triggerPhrases = triggers,
            steps = steps,
            unknownCapabilities = emptyList(),
            createdAt = now(),
            inputs = inputs,
            reviewSummary = review,
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
                append("reviewSummary:\n").append(proposal.reviewSummary)
                append("WAITING_FOR_USER_CONFIRMATION: supply this exact reviewSummary to halo_save_procedure after user review. No steps executed or authorized.")
            },
        )
    }

    private suspend fun save(toolCall: ToolCall): ToolExecutionResult = saveMutex.withLock { saveOnce(toolCall) }

    private suspend fun saveOnce(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: com.halo.runtime.cognition.tools.ToolRejectionKind = com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        if (toolCall.arguments.keys.any { it !in setOf("proposalId", "reviewSummary") }) return reject("Invalid arguments")
        val proposalId = (toolCall.arguments["proposalId"] as? String)?.trim().orEmpty()
        val proposal = synchronized(pending) { pending[proposalId] } ?: return reject("Unknown or expired proposal", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
        if (now() - proposal.createdAt !in 0..10 * 60_000L) return reject("Unknown or expired proposal", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
        if (!proposal.savable) return reject("That proposal cannot be saved", com.halo.runtime.cognition.tools.ToolRejectionKind.UNSUPPORTED_OPERATION)
        if ((proposal.steps.any { it.argumentsJson != null } || toolCall.arguments.containsKey("reviewSummary")) &&
            toolCall.arguments["reviewSummary"] != proposal.reviewSummary) return reject("Review the complete unchanged workflow before saving")
        if (proposal.steps.any { !capabilityKnown(it.capabilityId) }) return reject(
            "A proposed action is no longer registered. Review a new proposal before saving.",
            com.halo.runtime.cognition.tools.ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        if (proposal.steps.any { it.argumentsJson != null && descriptorFor(it.capabilityId)?.let(ProcedureContracts::fingerprint) != it.contractFingerprint })
            return reject("An action contract changed. Review a new proposal.")
        for (step in proposal.steps) {
            val literals = ProcedureContracts.literals(step) ?: continue
            if (validateTemplate(ToolCall("workflow-save", step.capabilityId, literals),
                    ProcedureContracts.inputTypes(step, proposal.inputs)) != null)
                return reject("An action schema or dependency changed. Review a new proposal.")
        }
        val timestamp = now()
        if (store.get(proposal.proposalId) == null && store.all().size >= 50) return reject("Workflow storage is full. Review and delete an old workflow first.")
        val procedure = LearnedProcedure(
            id = proposal.proposalId,
            name = proposal.name,
            description = proposal.description,
            triggerPhrases = proposal.triggerPhrases,
            steps = proposal.steps,
            origin = ProcedureOrigin.AGENT_PROPOSED,
            createdAt = timestamp,
            updatedAt = timestamp,
            inputs = proposal.inputs,
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
        if (toolCall.arguments.keys.any { it !in setOf("procedureId", "name", "inputs", "sourceVersion") }) return reject("Invalid arguments")
        if (toolCall.arguments.filterKeys { it != "inputs" }.values.any { it !is String }) return reject("Invalid arguments")
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
        if (procedure.schemaVersion != 1) return reject("Saved workflow version unsupported")
        val expectedVersion = toolCall.arguments["sourceVersion"] as? String
        if ((procedure.steps.any { it.argumentsJson != null } || expectedVersion != null) && expectedVersion != ProcedureContracts.version(procedure))
            return reject("Saved workflow version changed or missing. Read its exact sourceVersion first.")
        val inputObject = when (val value = toolCall.arguments["inputs"]) {
            null -> JSONObject()
            is Map<*, *> -> JSONObject(value)
            is JSONObject -> value
            else -> return reject("Inputs must be a named object")
        }
        if (inputObject.keys().asSequence().toSet() != procedure.inputs.map { it.name }.toSet()) return reject("Supply exactly the named workflow inputs")
        val inputs = inputObject.keys().asSequence().associateWith { key ->
            val value = inputObject.get(key)
            if (value !is String || value.length !in 1..2_000) return reject("Each input must be bounded nonempty text")
            value
        }
        if (procedure.steps.size !in 1..ProcedureProposal.MAX_STEPS || procedure.steps.any { !capabilityKnown(it.capabilityId) })
            return reject("The saved recipe needs review because an action is missing or its step limit changed.",
                com.halo.runtime.cognition.tools.ToolRejectionKind.CAPABILITY_UNAVAILABLE)

        val bound = try { procedure.steps.map { step ->
            ProcedureContracts.bind(step, inputs, descriptorFor(step.capabilityId), procedure.inputs)?.also { arguments ->
                if (validateCall(ToolCall("workflow-preview", step.capabilityId, arguments)) != null)
                    return reject("Bound action arguments do not match the current schema. Review inputs.")
            }
        } } catch (_: IllegalArgumentException) { return reject("Saved action contract changed or inputs invalid. Review a new workflow.") }
            catch (_: org.json.JSONException) { return reject("Saved workflow data unavailable") }
        if (bound.sumOf { it?.let { args -> JSONObject(args).toString().length } ?: 0 } + procedure.steps.sumOf { it.intent.length + it.capabilityId.length } > 2_500)
            return reject("Workflow inputs exceed the bounded preview size. Use shorter inputs.")
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
                    bound[index]?.let { append("   reviewed arguments: ").append(JSONObject(it)).append('\n') }
                }
                append("PREVIEW_ONLY: no step dispatched or completed. Use ordinary registered tools in this CognitiveSession, in order, with fresh source checks and individual policy/approval. Stop on denied, failed, cancelled or unknown result; never automatically replay a write. Saving this workflow grants no unattended authority or rollback guarantee.")
            },
        )
    }

    private suspend fun list(call: ToolCall): ToolExecutionResult {
        if (call.arguments.keys.any { it != "offset" }) return ToolExecutionResult.Rejected(call.id, call.functionName, "Invalid arguments")
        val offsetRaw = call.arguments["offset"]
        val offset = if (offsetRaw == null) 0 else (offsetRaw as? Number)?.toInt()
            ?: return ToolExecutionResult.Rejected(call.id, call.functionName, "Invalid offset")
        if (offset !in 0..50 || offsetRaw != null && (offsetRaw as Number).toDouble() != offset.toDouble())
            return ToolExecutionResult.Rejected(call.id, call.functionName, "Invalid offset")
        val all = store.all().take(50)
        var rows = all.drop(offset).take(3)
        fun output(): String = JSONObject().put("schemaVersion", 1)
            .put("nextOffset", if (offset + rows.size < all.size) offset + rows.size else JSONObject.NULL)
            .put("workflows", JSONArray(rows.map { row -> JSONObject().put("procedureId", row.id)
                .put("name", row.name).put("enabled", row.enabled).put("sourceVersion", ProcedureContracts.version(row))
                .put("stepCount", row.steps.size).put("inputs", JSONArray(row.inputs.take(8).map { JSONObject().put("name", it.name.take(40)).put("valueType", it.valueType) }))
                .put("typed", row.steps.all { it.argumentsJson != null }).put("executed", false) })).toString()
        while (rows.size > 1 && output().length > 3_800) rows = rows.dropLast(1)
        val result = output()
        if (result.length > 3_800) return ToolExecutionResult.Rejected(call.id, call.functionName, "Workflow metadata exceeds its supported bound")
        return ToolExecutionResult.Success(call.id, call.functionName, result)
    }

    private suspend fun change(call: ToolCall): ToolExecutionResult = saveMutex.withLock {
        fun reject(reason: String) = ToolExecutionResult.Rejected(call.id, call.functionName, reason)
        val deleting = call.functionName == "halo_procedure_delete"
        val keys = if (deleting) setOf("procedureId", "sourceVersion") else setOf("procedureId", "sourceVersion", "enabled")
        if (call.arguments.keys != keys) return@withLock reject("Invalid arguments")
        val id = call.arguments["procedureId"] as? String ?: return@withLock reject("Missing workflow ID")
        val version = call.arguments["sourceVersion"] as? String ?: return@withLock reject("Missing workflow version")
        val row = store.get(id) ?: return@withLock reject("Saved workflow unavailable")
        if (ProcedureContracts.version(row) != version) return@withLock reject("Saved workflow changed. Read it again.")
        val next = if (deleting) null else row.copy(enabled = call.arguments["enabled"] as? Boolean
            ?: return@withLock reject("Enabled must be boolean"), updatedAt = maxOf(now(), row.updatedAt + 1))
        if (!store.compareAndSet(row, next)) return@withLock reject("Saved workflow changed. Read it again.")
        ToolExecutionResult.Success(call.id, call.functionName, JSONObject().put("procedureId", row.id)
            .put("deleted", deleting).put("changed", true).put("executed", false)
            .put("sourceVersion", next?.let(ProcedureContracts::version) ?: JSONObject.NULL).toString())
    }

    private fun parseTriggers(raw: Any?): List<String>? {
        if (raw == null) return emptyList()
        val values: List<*> = when (raw) {
            is JSONArray -> { if (raw.length() > ProcedureProposal.MAX_TRIGGERS) return null; (0 until raw.length()).map { raw.get(it) } }
            is List<*> -> raw
            else -> return null
        }
        if (values.size > ProcedureProposal.MAX_TRIGGERS || values.any { it !is String || it.isBlank() || it.length > ProcedureProposal.MAX_TRIGGER_CHARS }) return null
        return values.map { it as String }
    }

    private fun parseSteps(raw: Any?): List<ProcedureStep>? {
        val items = when (raw) {
            is JSONArray -> { if (raw.length() > ProcedureProposal.MAX_STEPS) return null; (0 until raw.length()).map { raw.optJSONObject(it) } }
            is List<*> -> { if (raw.size > ProcedureProposal.MAX_STEPS) return null; raw.map { item ->
                when (item) {
                    is Map<*, *> -> org.json.JSONObject(item)
                    is org.json.JSONObject -> item
                    else -> null
                }
            } }
            else -> return null
        }
        if (items.any { it == null }) return null
        return try { items.filterNotNull().map { item ->
            ProcedureContracts.step(item, descriptorFor(item.optString("capabilityId")))
        } } catch (_: IllegalArgumentException) { null } catch (_: org.json.JSONException) { null }
    }
}
