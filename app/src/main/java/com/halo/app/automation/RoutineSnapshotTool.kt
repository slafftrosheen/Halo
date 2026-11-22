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
}

/** Produces a short local result. The saved routine owns its delivery and schedule. */
class RoutineSnapshotTool(
    private val read: suspend (RoutineSnapshotSection) -> String?,
) : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition(ID, ID,
        "Read one local summary for a routine: remaining items in today's Planner, tomorrow's Planner, current saved weather, or the latest current headline from selected Reads sources. Planner includes counts only. Missing or expired external data blocks the action. Does not fetch, speak or send anything by itself.",
        mapOf("type" to "object", "properties" to mapOf("section" to
            mapOf("type" to "string", "enum" to RoutineSnapshotSection.entries.map { it.name })),
            "required" to listOf("section"), "additionalProperties" to false)))

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        if (toolCall.functionName != ID)
            return ToolExecutionResult.Rejected(toolCall.id, ID, "Unknown routine action", ToolRejectionKind.TOOL_UNKNOWN)
        val section = RoutineSnapshotSection.entries.firstOrNull { it.name == toolCall.arguments["section"] }
        if (toolCall.arguments.keys != setOf("section") || section == null)
            return ToolExecutionResult.Rejected(toolCall.id, ID, "Choose a supported routine summary")
        val summary = try { read(section) } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        return if (summary.isNullOrBlank()) ToolExecutionResult.Rejected(toolCall.id, ID,
            "Current saved information is unavailable for this action.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
        else ToolExecutionResult.Success(toolCall.id, ID, summary.take(240))
    }

    companion object { const val ID = "halo_routine_snapshot" }
}
