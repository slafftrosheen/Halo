package com.halo.app.di.tools

import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.notebook.NotebookRepository
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.workspace.WorkspaceRepository
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.flow.first

class WorkspaceToolExecutor(
    private val notebookRepository: NotebookRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val workspaceContextStore: WorkspaceContextStore
) : ToolExecutor {

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_create_note",
            name = "halo_create_note",
            description = "Create a local note in Halo Notebook.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "body" to mapOf("type" to "string", "maxLength" to 20000)
                ),
                "required" to listOf("body"),
                "additionalProperties" to false
            )
        ),
        ToolDefinition(
            id = "halo_search_notebook",
            name = "halo_search_notebook",
            description = "Search local workspace notes and items.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "maxLength" to 500)
                ),
                "required" to listOf("query"),
                "additionalProperties" to false
            )
        ),
        ToolDefinition(
            id = "halo_add_to_working_set",
            name = "halo_add_to_working_set",
            description = "Add a workspace object to active Working Set context.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "objectId" to mapOf("type" to "string", "maxLength" to 100)
                ),
                "required" to listOf("objectId"),
                "additionalProperties" to false
            )
        ),
        ToolDefinition(
            id = "halo_remove_from_working_set",
            name = "halo_remove_from_working_set",
            description = "Remove a workspace object from Working Set context.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "objectId" to mapOf("type" to "string", "maxLength" to 100)
                ),
                "required" to listOf("objectId"),
                "additionalProperties" to false
            )
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)

        return when (toolCall.functionName) {
            "halo_create_note" -> {
                val body = toolCall.arguments["body"]?.toString()?.trim().orEmpty()
                if (body.isBlank()) return reject("Body parameter is required")
                val note = notebookRepository.createNote(body)
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Created note: ${note.id} - '${note.title}'")
            }
            "halo_search_notebook" -> {
                val query = toolCall.arguments["query"]?.toString()?.trim().orEmpty()
                val notes = notebookRepository.observeNotes(query = query).first().take(5)
                val summary = notes.joinToString("\n") { "- [${it.id}] ${it.title}" }.ifEmpty { "No matching notes found." }
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, summary)
            }
            "halo_add_to_working_set" -> {
                val objectId = toolCall.arguments["objectId"]?.toString()?.trim().orEmpty()
                if (objectId.isBlank()) return reject("objectId is required")
                workspaceContextStore.select(objectId)
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Added $objectId to Working Set")
            }
            "halo_remove_from_working_set" -> {
                val objectId = toolCall.arguments["objectId"]?.toString()?.trim().orEmpty()
                if (objectId.isBlank()) return reject("objectId is required")
                workspaceContextStore.remove(objectId)
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Removed $objectId from Working Set")
            }
            else -> reject("Unsupported workspace tool: ${toolCall.functionName}")
        }
    }
}
