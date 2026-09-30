package com.halo.app.di.tools

import com.halo.runtime.cognition.routine.Routine
import com.halo.runtime.cognition.routine.RoutineAction
import com.halo.runtime.cognition.routine.RoutineId
import com.halo.runtime.cognition.routine.RoutineRepository
import com.halo.runtime.cognition.routine.RoutineScheduleResult
import com.halo.runtime.cognition.routine.RoutineScheduler
import com.halo.runtime.cognition.routine.*
import com.halo.runtime.cognition.tools.ToolGrantPreviewProvider
import com.halo.app.automation.RoutineArguments
import com.halo.app.data.routine.RoutineCodec
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * MINING-P1-001 model-callable routine creation.
 *
 * Halo already owns `RoutineRepository`, `AndroidRoutineScheduler`, `RoutineExecutor` validation and
 * reconciliation. This is only a bridge: it prepares, validates and persists a real routine through
 * those existing components. No scheduler, policy or action vocabulary is duplicated.
 *
 * The tool is `ConfirmationPolicy.ALWAYS`, so the existing durable approval surface is what actually
 * asks the user "Create routine?". Persistence happens only after that approval, which is exactly the
 * property the batch requires.
 *
 * Result truth is reported in parts — SAVED, SCHEDULED, BLOCKED, SCHEDULE_FAILED — because "routine
 * created" must not imply Android scheduling succeeded.
 */
class RoutineCreationToolExecutor(
    private val repository: RoutineRepository,
    private val scheduler: RoutineScheduler,
    /** `RoutineExecutor::validate` — the single authority on what may run unattended. */
    private val validate: (Routine) -> Unit,
    private val grantCopy: (Routine) -> String = { routineGrantSummary(it) },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Instant = Instant::now,
) : ToolExecutor, ToolGrantPreviewProvider {

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_create_routine",
            name = "halo_create_routine",
            description = "Propose a Halo automation with a tagged trigger, optional conditions, delivery and existing " +
                "tool actions. Requires the user's approval and only succeeds for actions that are " +
                "allowed to run unattended.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string", "maxLength" to 80),
                    "time" to mapOf("type" to "string", "description" to "Legacy 24-hour HH:mm; use time OR trigger"),
                    "trigger" to mapOf("type" to "string", "maxLength" to 2048, "description" to "Tagged JSON object. Types: time (time, zone, days), once (at ISO instant), connected/disconnected (device), app_started/notification (package; notification coalesce seconds), battery_above/battery_below (percent), charging_started/charging_stopped/network_available/network_lost."),
                    "conditions" to mapOf("type" to "string", "maxLength" to 4096, "description" to "JSON array: device (id), provider (id), battery (percent), network (required boolean). No text predicates."),
                    "delivery" to mapOf("type" to "string", "maxLength" to 512, "description" to "Tagged JSON object: silent (default), notification, in_app, wearable (device)."),
                    "minimum_interval_seconds" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 86400),
                    "days" to mapOf(
                        "type" to "array",
                        "items" to mapOf(
                            "type" to "string",
                            "enum" to DAY_TOKENS,
                        ),
                        "maxItems" to 7,
                    ),
                    "actions" to mapOf(
                        "type" to "array",
                        "minItems" to 1,
                        "maxItems" to MAX_ACTIONS,
                        "items" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "toolId" to mapOf("type" to "string", "maxLength" to 120),
                                "arguments" to mapOf("type" to "string", "maxLength" to 8192),
                            ),
                            "required" to listOf("toolId"),
                            "additionalProperties" to false,
                        ),
                    ),
                ),
                "required" to listOf("name", "actions"),
                "additionalProperties" to false,
            ),
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)
        if (toolCall.functionName != "halo_create_routine") {
            return reject("Unsupported routine tool: ${toolCall.functionName}")
        }
        val proposal = try { proposal(toolCall) } catch(e: IllegalArgumentException) { return reject(e.message ?: "Invalid automation") }
            catch(e: org.json.JSONException) { return reject("Invalid automation definition") }
            catch(e: IllegalStateException) { return reject(e.message ?: "Invalid automation") }
        val name = proposal.name
        val routine = proposal.copy(grantDigest = com.halo.app.automation.RoutineArguments.grant(proposal))

        /*
         * MINING-P1-001: policy is checked BEFORE anything is saved. Halo never stores a routine that
         * is guaranteed to be blocked at run time.
         */
        try {
            validate(routine)
        } catch (invalid: IllegalArgumentException) {
            return reject("Cannot be automated: ${invalid.message ?: "action not allowed unattended"}")
        } catch (invalid: IllegalStateException) {
            return reject("Cannot be automated: ${invalid.message ?: "action not allowed unattended"}")
        }

        repository.upsert(routine)
        val saved = "SAVED routine=${routine.id.value.take(8)} name=\"$name\""

        return try {
            when (val scheduled = scheduler.schedule(routine)) {
                is RoutineScheduleResult.Scheduled -> {
                    repository.upsert(routine.copy(nextRunAt = scheduled.at))
                    ToolExecutionResult.Success(
                        toolCall.id,
                        toolCall.functionName,
                        "$saved\nSCHEDULED next=${scheduled.at}",
                    )
                }
                is RoutineScheduleResult.EventDriven -> ToolExecutionResult.Success(
                    toolCall.id,
                    toolCall.functionName,
                    "$saved\nSCHEDULED event-driven trigger",
                )
                is RoutineScheduleResult.Blocked -> ToolExecutionResult.Success(
                    toolCall.id,
                    toolCall.functionName,
                    "$saved\nBLOCKED reason=${scheduled.reason.take(160)}",
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Saved but not scheduled: both facts are reported separately.
            ToolExecutionResult.Success(
                toolCall.id,
                toolCall.functionName,
                "$saved\nSCHEDULE_FAILED reason=${(failure.message ?: "scheduler error").take(160)}",
            )
        }
    }

    private fun proposal(call: ToolCall): Routine {
        require(call.functionName == "halo_create_routine")
        require(call.arguments.keys.all { it in setOf("name", "time", "days", "actions", "trigger", "conditions", "delivery", "minimum_interval_seconds") }) { "Invalid arguments" }
        val name = (call.arguments["name"] as? String)?.trim().orEmpty()
        require(name.isNotBlank() && name.length <= 80) { "A routine needs a short name" }
        val codec = RoutineCodec()
        val triggerRaw = call.arguments["trigger"] as? String
        require(triggerRaw == null || call.arguments["time"] == null) { "Use time or trigger" }
        val trigger = if(triggerRaw != null) {
            RoutineArguments.decode(triggerRaw)
            codec.decodeTrigger(triggerRaw, 1)
        } else RoutineTrigger.AtTime(requireNotNull(parseTime(call.arguments["time"] as? String)) { "Time must be HH:mm" },
            requireNotNull(parseDays(call.arguments["days"])) { "Invalid days" })
        val conditionsRaw = (call.arguments["conditions"] as? String) ?: "[]"
        RoutineArguments.decode("{\"conditions\":$conditionsRaw}")
        val deliveryRaw = (call.arguments["delivery"] as? String) ?: "{\"type\":\"silent\"}"
        RoutineArguments.decode(deliveryRaw)
        val timestamp = now()
        val r = Routine(RoutineId(newId()), name, trigger = trigger, conditions = codec.decodeConditions(conditionsRaw, 1),
            actions = requireNotNull(parseActions(call.arguments["actions"])) { "Invalid actions" }, createdAt = timestamp, updatedAt = timestamp,
            delivery = codec.decodeDelivery(deliveryRaw), minimumIntervalSeconds = (call.arguments["minimum_interval_seconds"] as? Number)?.toLong() ?: 0)
        validate(r)
        return r
    }
    override suspend fun grantSummary(toolCall: ToolCall): String {
        val r = proposal(toolCall)
        return grantCopy(r)
    }
    private fun parseTime(raw: String?): LocalTime? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return try {
            LocalTime.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseDays(raw: Any?): Set<DayOfWeek>? {
        if (raw == null) return emptySet()
        val values = when (raw) {
            is JSONArray -> (0 until raw.length()).mapNotNull { raw.optString(it).takeIf { it.isNotBlank() } }
            is List<*> -> raw.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            else -> return null
        }
        if (values.isEmpty()) return emptySet()
        val result = mutableSetOf<DayOfWeek>()
        for (value in values) {
            when (value.trim().uppercase()) {
                "DAILY", "EVERY_DAY" -> return emptySet()
                "WEEKDAYS", "WEEKDAY" -> result += WEEKDAYS
                "WEEKEND", "WEEKENDS" -> result += setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                else -> {
                    val day = runCatching { DayOfWeek.valueOf(value.trim().uppercase()) }.getOrNull() ?: return null
                    result += day
                }
            }
        }
        return result
    }

    private fun parseActions(raw: Any?): List<RoutineAction>? {
        val items = when (raw) {
            is JSONArray -> (0 until raw.length()).map { raw.optJSONObject(it) }
            is List<*> -> raw.map { item ->
                when (item) {
                    is Map<*, *> -> JSONObject(item)
                    is JSONObject -> item
                    else -> null
                }
            }
            else -> return null
        }
        if (items.any { it == null }) return null
        return items.filterNotNull().map { item ->
            val toolId = item.optString("toolId").trim()
            if (toolId.isEmpty()) return null
            val arguments = item.optString("arguments").ifBlank { "{}" }
            // Bounded before it ever reaches the registry.
            if (arguments.toByteArray(Charsets.UTF_8).size > 8192) return null
            runCatching { RoutineArguments.decode(arguments) }.getOrNull() ?: return null
            RoutineAction(toolId = toolId.take(120), argumentsJson = arguments)
        }
    }

    private companion object {
        const val MAX_ACTIONS = 10
        val WEEKDAYS = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        val DAY_TOKENS = listOf(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY",
            "DAILY", "WEEKDAYS", "WEEKEND",
        )
    }
}
