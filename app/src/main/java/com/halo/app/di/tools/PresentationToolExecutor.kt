package com.halo.app.di.tools

import com.halo.runtime.cognition.interaction.PresentationChoice
import com.halo.runtime.cognition.interaction.PresentationCommitRegistry
import com.halo.runtime.cognition.interaction.PresentationDirective
import com.halo.runtime.cognition.interaction.PresentationDirectiveEntry
import com.halo.runtime.cognition.interaction.PresentationDirectiveItem
import com.halo.runtime.cognition.interaction.PresentationProvenance
import com.halo.runtime.cognition.interaction.PresentationStatusState
import com.halo.runtime.cognition.interaction.PresentationStepState
import com.halo.runtime.cognition.interaction.PresentationSurfaceProjection
import com.halo.runtime.cognition.interaction.PresentationTemplate
import com.halo.runtime.cognition.interaction.PresentationTemplates
import com.halo.runtime.cognition.interaction.PresentationValidator
import com.halo.runtime.cognition.interaction.StructuredPresentation
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * MINING-P2-001 `halo_present`.
 *
 * The agent asks for a richer presentation than prose. This is the only supported path: Halo does not
 * parse `<a2ui-json>` out of arbitrary assistant text, and tool/schema transport fits the existing
 * agent architecture instead of inventing a parallel one.
 *
 * The tool:
 *
 * 1. parses a FLAT, model-friendly directive (a template plus shallow lists — a 0.6B model should
 *    never have to author a nested component tree);
 * 2. expands it deterministically through [PresentationTemplates];
 * 3. validates it hard ([PresentationValidator]) including that any `ChoiceGroup` resolves an
 *    *already durable* input request;
 * 4. projects an ephemeral surface revision;
 * 5. returns a tiny machine result.
 *
 * It grants nothing. Presentation is a view; approval truth stays in `AgentInputRequest`, execution
 * truth stays in the `AgentRun` journal.
 */
class PresentationToolExecutor(
    private val projection: PresentationSurfaceProjection,
    /**
     * Answers "does this durable input request exist and own exactly these option ids?".
     * Provided by the host from the journal. A resolver that always returns false keeps the strict
     * behaviour: no generated choice surface is publishable.
     */
    private val choiceBinding: suspend () -> PresentationValidator.ChoiceBindingResolver,
    /**
     * Recovers the options of a pending durable choice request. A choice presentation never invents
     * option ids; Halo reads them from the request the user actually has to answer.
     */
    private val pendingChoiceOptions: suspend (String) -> List<PresentationChoice>? = { null },
    /**
     * MINING-P2-001: remembers a validated presentation so the response committer can carry it into the
     * committed turn. Optional and purely additive — presentation never becomes execution truth.
     */
    private val presentations: PresentationCommitRegistry? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val newSurfaceId: () -> String = { "presentation-${UUID.randomUUID()}" },
) : ToolExecutor {

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = TOOL_ID,
            name = TOOL_ID,
            description = "Present bounded structured information to the user (a card of key/value " +
                "facts, a comparison, a checklist, search results, a status or a choice). " +
                "Presentation only: it cannot perform any action.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "template" to mapOf(
                        "type" to "string",
                        "enum" to listOf(
                            "KEY_VALUE_SUMMARY", "COMPARISON", "CHECKLIST",
                            "SEARCH_RESULTS", "STATUS", "CHOICE",
                        ),
                    ),
                    "title" to mapOf("type" to "string", "maxLength" to 140),
                    "subtitle" to mapOf("type" to "string", "maxLength" to 300),
                    "conclusion" to mapOf("type" to "string", "maxLength" to 700),
                    "status" to mapOf(
                        "type" to "string",
                        "enum" to listOf("NEUTRAL", "INFO", "SUCCESS", "WARNING", "ERROR"),
                    ),
                    "progress" to mapOf("type" to "number", "minimum" to 0, "maximum" to 1),
                    "entries" to mapOf(
                        "type" to "array",
                        "maxItems" to 20,
                        "items" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "label" to mapOf("type" to "string", "maxLength" to 80),
                                "value" to mapOf("type" to "string", "maxLength" to 300),
                            ),
                            "required" to listOf("label", "value"),
                            "additionalProperties" to false,
                        ),
                    ),
                    "items" to mapOf(
                        "type" to "array",
                        "maxItems" to 20,
                        "items" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "text" to mapOf("type" to "string", "maxLength" to 300),
                                "state" to mapOf(
                                    "type" to "string",
                                    "enum" to listOf("PENDING", "ACTIVE", "COMPLETED", "FAILED", "SKIPPED"),
                                ),
                            ),
                            "required" to listOf("text"),
                            "additionalProperties" to false,
                        ),
                    ),
                    "columns" to mapOf(
                        "type" to "array",
                        "maxItems" to 5,
                        "items" to mapOf("type" to "string", "maxLength" to 120),
                    ),
                    "inputRequestId" to mapOf("type" to "string", "maxLength" to 120),
                    "allowMultiple" to mapOf("type" to "boolean"),
                ),
                "required" to listOf("template"),
                "additionalProperties" to false,
            ),
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)
        if (toolCall.functionName != TOOL_ID) return reject("Unsupported presentation tool: ${toolCall.functionName}")

        val args = toolCall.arguments
        val allowed = setOf(
            "template", "title", "subtitle", "conclusion", "status", "progress",
            "entries", "items", "columns", "inputRequestId", "allowMultiple",
        )
        if (args.keys.any { it !in allowed }) return reject("Invalid arguments")

        val template = PresentationTemplate.parse(args["template"]?.toString())
            ?: return reject("template must be one of KEY_VALUE_SUMMARY, COMPARISON, CHECKLIST, SEARCH_RESULTS, STATUS, CHOICE")
        if (template == PresentationTemplate.CUSTOM) {
            return reject("CUSTOM templates are not accepted from this tool")
        }

        val entries = parseEntries(args["entries"]) ?: return reject("entries must be label/value pairs")
        val items = parseItems(args["items"]) ?: return reject("items must be text entries")
        val columns = parseStrings(args["columns"]) ?: return reject("columns must be short strings")
        val status = runCatching { PresentationStatusState.valueOf(args["status"]?.toString()?.uppercase() ?: "INFO") }
            .getOrDefault(PresentationStatusState.INFO)
        val progress = (args["progress"] as? Number)?.toFloat()?.takeIf { !it.isNaN() && it in 0f..1f }
        val inputRequestId = args["inputRequestId"]?.toString()?.trim().orEmpty()
        val allowMultiple = args["allowMultiple"] as? Boolean ?: false

        if (template == PresentationTemplate.CHOICE && inputRequestId.isBlank()) {
            return reject("A choice presentation must reference an existing input request")
        }

        val directive = PresentationDirective(
            template = template,
            title = args["title"]?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            subtitle = args["subtitle"]?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            conclusion = args["conclusion"]?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            entries = entries,
            items = items,
            columns = columns,
            status = status,
            progress = progress,
            inputRequestId = inputRequestId.takeIf { it.isNotEmpty() },
            allowMultiple = allowMultiple,
        )

        /*
         * CHOICE options come from the durable request, never from the model: the model supplies the
         * prompt, Halo supplies what the user is actually allowed to pick.
         */
        if (template == PresentationTemplate.CHOICE) {
            val options = choiceOptionsFor(inputRequestId)
            val presentation = if (options.isNullOrEmpty()) null else PresentationTemplates.choice(
                prompt = directive.title ?: "Choose an option",
                inputRequestId = inputRequestId,
                options = options,
                allowMultiple = allowMultiple,
            )
            if (presentation == null) return reject("That input request is no longer pending")
            return publish(
                toolCall,
                presentation,
                fallback = directive.title ?: "Choose an option",
                template = PresentationTemplate.CHOICE,
            )
        }

        val presentation = PresentationTemplates.fromDirective(directive)
        return publish(
            toolCall,
            presentation,
            fallback = directive.conclusion ?: directive.title ?: "Presented information",
            template = directive.template,
        )
    }

    /**
     * Option ids are recovered from the bound request through [choiceBinding]'s authority by asking
     * it to confirm the ids Halo itself derived. When the request is unknown, nothing is published.
     */
    private suspend fun choiceOptionsFor(inputRequestId: String): List<PresentationChoice>? {
        val candidates = pendingChoiceOptions(inputRequestId) ?: return null
        val ids = candidates.map { it.optionId }.toSet()
        if (ids.isEmpty()) return null
        return if (choiceBinding().allows(inputRequestId, ids)) candidates else null
    }

    private suspend fun publish(
        toolCall: ToolCall,
        presentation: StructuredPresentation,
        fallback: String,
        template: PresentationTemplate?,
    ): ToolExecutionResult {
        val verdict = PresentationValidator.validate(presentation, choiceBinding())
        if (verdict is PresentationValidator.Result.Invalid) {
            // Bounded, explicit refusal: never a silently truncated UI tree.
            return ToolExecutionResult.Rejected(
                toolCall.id,
                toolCall.functionName,
                "Presentation rejected: " + verdict.reasons.joinToString("; ").take(400),
            )
        }
        val validated = verdict as PresentationValidator.Result.Valid
        val surfaceId = newSurfaceId()
        val timestamp = now()
        val revision = projection.nextRevision(surfaceId, timestamp)
        val applied = projection.apply(
            com.halo.runtime.cognition.interaction.PresentationProjectionEvent.SurfaceCreated(
                revision = revision,
                presentation = presentation,
            )
        )
        if (!applied) return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Presentation was not accepted")

        // Staged, not committed: the runtime promotes it only when it observes this call succeed, and
        // the committer consumes it once. Nothing here is durable.
        presentations?.stage(
            surfaceId = surfaceId,
            presentation = presentation,
            template = template?.name,
            nodeCount = validated.nodeCount,
        )

        return ToolExecutionResult.Success(
            toolCall.id,
            toolCall.functionName,
            buildString {
                append("status=success\n")
                append("surface_id=").append(surfaceId).append('\n')
                append("schema=").append(presentation.schemaVersion).append('\n')
                append("nodes=").append(validated.nodeCount).append('\n')
                append("rendered=true\n")
                // This is the ONLY text speech or a text-only host will ever use for this surface.
                append("text_fallback=").append(fallback.take(200))
                append("\nNote: presentation is display only. Continue with normal actions if the user asks for work.")
            },
        )
    }

    fun provenanceFor(providerId: String?, modelId: String?, runId: String?): PresentationProvenance =
        PresentationProvenance(
            sourceRunId = runId?.let { com.halo.runtime.cognition.agent.AgentRunId(it) },
            providerId = providerId,
            modelId = modelId,
            generatedAtMs = now(),
        )

    private fun parseEntries(raw: Any?): List<PresentationDirectiveEntry>? {
        if (raw == null) return emptyList()
        val items = when (raw) {
            is JSONArray -> (0 until raw.length()).map { raw.optJSONObject(it) }
            is List<*> -> raw.map { it as? JSONObject }
            else -> return null
        }
        if (items.any { it == null }) return null
        return items.filterNotNull().map { item ->
            val label = item.optString("label").trim()
            val value = item.optString("value").trim()
            if (label.isEmpty() || value.isEmpty()) return null
            PresentationDirectiveEntry(
                label = label.take(80),
                value = value.take(300),
                state = runCatching { PresentationStatusState.valueOf(item.optString("state", "INFO").uppercase()) }
                    .getOrDefault(PresentationStatusState.INFO),
            )
        }
    }

    private fun parseItems(raw: Any?): List<PresentationDirectiveItem>? {
        if (raw == null) return emptyList()
        val items = when (raw) {
            is JSONArray -> (0 until raw.length()).map { raw.optJSONObject(it) }
            is List<*> -> raw.map { it as? JSONObject }
            else -> return null
        }
        if (items.any { it == null }) return null
        return items.filterNotNull().map { item ->
            val text = item.optString("text").trim()
            if (text.isEmpty()) return null
            PresentationDirectiveItem(
                text = text.take(300),
                state = runCatching { PresentationStepState.valueOf(item.optString("state", "PENDING").uppercase()) }
                    .getOrDefault(PresentationStepState.PENDING),
            )
        }
    }

    private fun parseStrings(raw: Any?): List<String>? {
        if (raw == null) return emptyList()
        val values = when (raw) {
            is JSONArray -> (0 until raw.length()).map { raw.optString(it) }
            is List<*> -> raw.map { it?.toString().orEmpty() }
            else -> return null
        }
        if (values.size > 5) return null
        return values.map { it.take(120) }
    }

    companion object {
        const val TOOL_ID = "halo_present"
    }
}
