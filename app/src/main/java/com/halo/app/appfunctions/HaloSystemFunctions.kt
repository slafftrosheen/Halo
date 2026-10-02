package com.halo.app.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.appfunctions.AppFunctionInvalidArgumentException
import com.halo.app.di.ManualDependencyGraph
import com.halo.runtime.cognition.agent.*
import com.halo.cognition.language.InteractionId
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Optional system entry point. Every write uses the same host execution authority. */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(serviceName = "HaloAppFunctionService", appFunctionXmlFileName = "halo_app_functions")
abstract class HaloSystemFunctions : AppFunctionService() {
    /** Create a notebook note containing the supplied text.
     * @param body Note content, limited to 2000 characters.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun createNote(body: String): String = execute("halo_create_note", "body", body, 2000)

    /** Search the user's notebook for a phrase.
     * @param query Search phrase, limited to 200 characters.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchNotebook(query: String): String = execute("halo_search_notebook", "query", query, 200)

    /** Add an existing notebook object to the explicit working set.
     * @param objectId Existing object identifier, limited to 100 characters.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addToWorkingSet(objectId: String): String = execute("halo_add_to_working_set", "objectId", objectId, 100)

    /** Report Halo's runtime and skill registration status without reading private content. */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getHaloStatus(): String = withContext(Dispatchers.IO) {
        val audit = ManualDependencyGraph.skillRuntimeAudit.inspect()
        "Halo: ${audit.skillCount} skills, ${audit.enabledSkillCount} enabled; ${audit.unresolvedToolIds.size} unresolved actions"
    }

    private suspend fun execute(tool: String, key: String, value: String, maximum: Int): String = withContext(Dispatchers.IO) {
        if (value.isBlank() || value.length > maximum) throw AppFunctionInvalidArgumentException("Invalid $key")
        val id = UUID.randomUUID().toString()
        val coordinator = ManualDependencyGraph.agentRunCoordinator
        val run = coordinator.start(AgentRequest(InteractionId(id), "System function: $tool", kind = AgentRunKind.NATIVE))
        coordinator.transition(run, AgentRunState.EXECUTING)
        try {
            val result = ManualDependencyGraph.skillExecutionService.execute(run, InteractionId(id), id,
                listOf(ToolCall(id, tool, mapOf(key to value))), true, setOf(tool))
            if (result is com.halo.runtime.cognition.turn.ToolTurnResult.Completed) {
                if (result.toolMessages.any { "status=rejected" in it.content || "status=failed" in it.content }) coordinator.fail(run, "System function rejected") else coordinator.complete(run)
                result.toolMessages.joinToString("\n") { it.content }
            } else "Action requires approval or setup in Halo"
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            coordinator.cancel(run, "System function cancelled"); throw cancelled
        } catch (error: Exception) {
            coordinator.fail(run, "System function failed"); throw error
        }
    }
}
