package com.halo.app.di.tools

import com.halo.runtime.cognition.routine.Routine
import com.halo.runtime.cognition.routine.RoutineAction
import com.halo.runtime.cognition.routine.RoutineId
import com.halo.runtime.cognition.routine.RoutineRepository
import com.halo.runtime.cognition.routine.RoutineScheduleResult
import com.halo.runtime.cognition.routine.RoutineScheduler
import com.halo.runtime.cognition.routine.RoutineTrigger
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
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Instant = Instant::now,
) : ToolExecutor {

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_create_routine",
            name = "halo_create_routine",
            description = "Create a scheduled Halo routine from a time-of-day trigger and existing " +
                "tool actions. Requires the user's approval and only succeeds for actions that are " +
                "allowed to run unattended.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string", "maxLength" to 80),
                    "time" to mapOf("type" to "string", "description" to "24-hour HH:mm"),
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
                                "arguments" to mapOf("type" to "string", "maxLength" to 1024),
                            ),
                            "required" to listOf("toolId"),
                            "additionalProperties" to false,
                        ),
                    ),
                ),
                "required" to listOf("name", "time", "actions"),
                "additionalProperties" to false,
            ),
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)
        if (toolCall.functionName != "halo_create_routine") {
            return reject("Unsupported routine tool: ${toolCall.functionName}")
        }
        val allowedKeys = setOf("name", "time", "days", "actions")
        if (toolCall.arguments.keys.any { it !in allowedKeys }) return reject("Invalid arguments")

        val name = toolCall.arguments["name"]?.toString()?.trim().orEmpty()
        if (name.isBlank() || name.length > 80) return reject("A routine needs a short name")

        val time = parseTime(toolCall.arguments["time"]?.toString()) ?: return reject("Time must be HH:mm")
        val days = parseDays(toolCall.arguments["days"]) ?: return reject("Unrecognized day list")
        val actions = parseActions(toolCall.arguments["actions"]) ?: return reject("Actions must be a list of toolId entries")
        if (actions.isEmpty()) return reject("A routine needs at least one action")
        if (actions.size > MAX_ACTIONS) return reject("At most $MAX_ACTIONS actions are supported")

        val timestamp = now()
        val routine = Routine(
            id = RoutineId(newId()),
            name = name,
            enabled = true,
            trigger = RoutineTrigger.AtTime(localTime = time, days = days),
            conditions = emptyList(),
            actions = actions,
            createdAt = timestamp,
            updatedAt = timestamp,
        )

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
            if (arguments.length > 1024) return null
            runCatching { JSONObject(arguments) }.getOrNull() ?: return null
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
