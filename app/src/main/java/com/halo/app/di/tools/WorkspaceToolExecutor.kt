package com.halo.app.di.tools

import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.notebook.NotebookRepository
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.workspace.WorkspaceRepository
import com.halo.runtime.cognition.context.AssistantContextRepository
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.study.StudySession
import com.halo.sdk.runtime.study.TutorStage
import kotlinx.coroutines.flow.first
import java.util.UUID

class WorkspaceToolExecutor(
    private val notebookRepository: NotebookRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val workspaceContextStore: WorkspaceContextStore,
    private val assistantContextRepository: AssistantContextRepository? = null
) : ToolExecutor {

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition("halo_get_workspace_object", "halo_get_workspace_object", "Read an existing workspace object and its note content.",
            mapOf("type" to "object", "properties" to mapOf("objectId" to mapOf("type" to "string", "maxLength" to 100)),
                "required" to listOf("objectId"), "additionalProperties" to false)),
        ToolDefinition(
            id = "halo_create_note",
            name = "halo_create_note",
            description = "Create a local note in Halo Notebook.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "body" to mapOf("type" to "string", "maxLength" to 2000)
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
            id = "halo_study_with_halo",
            name = "halo_study_with_halo",
            description = "Start a study/tutoring session for a given note or workspace object.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "objectId" to mapOf("type" to "string", "description" to "ID of the workspace object or note")
                ),
                "required" to listOf("objectId"),
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

        val key = when (toolCall.functionName) {
            "halo_create_note" -> "body"
            "halo_search_notebook" -> "query"
            else -> "objectId"
        }
        val value = toolCall.arguments[key] as? String ?: return reject("Expected $key text")
        val limit = if (key == "body") 2000 else if (key == "query") 500 else 100
        if (toolCall.arguments.keys != setOf(key) || value.length > limit || (key != "query" && value.isBlank())) return reject("Invalid $key")
        if (key == "objectId") {
            val item = workspaceRepository.get(WorkspaceObjectId(value.trim())) ?: return reject("Workspace object not found")
            if (item.lifecycle == com.halo.app.data.notebook.WorkspaceObjectLifecycle.TRASHED) return reject("Workspace object is in trash")
        }
        return when (toolCall.functionName) {
            "halo_get_workspace_object" -> {
                val item = requireNotNull(workspaceRepository.get(WorkspaceObjectId(value.trim())))
                val note = notebookRepository.getNote(item.id.value)
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                    "[${item.id.value}] ${item.type}: ${item.title}\n${note?.body.orEmpty()}".take(4000))
            }
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
            "halo_save_screen_to_notebook" -> {
                val interactionId = toolCall.arguments["interactionId"]?.toString()?.trim().orEmpty()
                if (interactionId.isBlank()) return reject("interactionId is required")
                val screen = assistantContextRepository?.screenFor(interactionId) ?: return reject("No screen context available for interaction $interactionId")
                val content = "App: ${screen.packageName}\nActivity: ${screen.activityName}\nTitle: ${screen.title}\nWeb URI: ${screen.webUri}\nNodes:\n" +
                    screen.nodes.take(20).joinToString("\n") { "- ${it.text ?: it.contentDescription}" }
                val note = notebookRepository.createNote(content)
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Saved screen to notebook as note ${note.id}")
            }
            "halo_study_with_halo" -> {
                val objectId = toolCall.arguments["objectId"]?.toString()?.trim().orEmpty()
                if (objectId.isBlank()) return reject("objectId is required")
                val session = StudySession(
                    id = UUID.randomUUID().toString(),
                    assignmentLanguage = null,
                    explanationLanguage = null,
                    sourceObjectIds = listOf(objectId),
                    interactionId = null,
                    tutorStage = TutorStage.ASK
                )
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Started study session ${session.id} for object $objectId in ASK stage.")
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
