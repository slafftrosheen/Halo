package com.halo.app.automation

import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.runtime.cognition.tools.ToolRejectionKind
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException

enum class RoutineSnapshotSection(val title: String) {
    TODAY("Today's Planner"),
    TOMORROW("Tomorrow's Planner"),
    WEATHER("Saved weather"),
    HEADLINE("Latest saved headline"),
    AGENDA("Upcoming Organizer events"),
    TASKS("Active tasks"),
    REMINDERS("Active reminders"),
    ALARMS_TIMERS("Active alarms and timers"),
    CLOCK("Local date and time"),
    BRIEF("Saved daily brief"),
    DEVICE("Phone battery"),
}

/** Produces a short local result. The saved routine owns its delivery and schedule. */
class RoutineSnapshotTool(
    private val unavailable: (RoutineSnapshotSection) -> String = { "Summary unavailable for this run." },
    private val read: suspend (RoutineSnapshotSection) -> String?,
) : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition(ID, ID,
        "Read one local summary for a routine. Organizer summaries contain counts. Use allow_unavailable to report a missing saved item and continue. Reads saved information without fetching or delivering it.",
        mapOf("type" to "object", "properties" to mapOf(
            "section" to mapOf("type" to "string", "enum" to RoutineSnapshotSection.entries.map { it.name }),
            "allow_unavailable" to mapOf("type" to "boolean")),
            "required" to listOf("section"), "additionalProperties" to false)))

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        if (toolCall.functionName != ID)
            return ToolExecutionResult.Rejected(toolCall.id, ID, "Unknown routine action", ToolRejectionKind.TOOL_UNKNOWN)
        val section = RoutineSnapshotSection.entries.firstOrNull { it.name == toolCall.arguments["section"] }
        val args = toolCall.arguments
        if (section == null || args.keys.any { it != "section" && it != "allow_unavailable" } ||
            "allow_unavailable" in args && args["allow_unavailable"] !is Boolean)
            return ToolExecutionResult.Rejected(toolCall.id, ID, "Choose a supported routine summary")
        fun rejectUnavailable() = ToolExecutionResult.Rejected(toolCall.id, ID,
            "Current saved information is unavailable for this action.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
        val summary = try { read(section) } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return rejectUnavailable() }
        val result = summary?.takeIf(String::isNotBlank)
            ?: if (args["allow_unavailable"] == true) unavailable(section) else return rejectUnavailable()
        return ToolExecutionResult.Success(toolCall.id, ID, result.take(240))
    }

    companion object { const val ID = "halo_routine_snapshot" }
}
