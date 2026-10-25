package com.halo.app.di.tools

import com.halo.app.data.WorkspaceContextStore
import com.halo.app.data.notebook.NotebookRepository
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.workspace.WorkspaceRepository
import com.halo.app.study.StudyStartResult
import com.halo.runtime.cognition.context.AssistantContextRepository
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.flow.first

class WorkspaceToolExecutor(
    private val notebookRepository: NotebookRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val workspaceContextStore: WorkspaceContextStore,
    private val assistantContextRepository: AssistantContextRepository? = null,
    /** Read-only Study description; the runtime sees the portable session contract, not storage. */
    private val studySessionDescriptions: (suspend (String) -> String?)? = null,
    private val continuity: WorkspaceContinuityActions? = null,
    private val canvas: WorkspaceCanvasActions? = null,
    private val exactStudyStarter: (suspend (String, String) -> StudyStartResult)? = null,
    private val studyActions: WorkspaceStudyActions? = null,
    private val noteActions: WorkspaceNoteActions? = null,
    private val notePurgeActions: WorkspaceNotePurgeActions? = null,
    private val noteAttachmentActions: WorkspaceNoteAttachmentActions? = null,
    private val remoteInk: RemoteInkActions? = null,
) : ToolExecutor {

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition("halo_create_empty_note", "halo_create_empty_note", "Create an empty local note for the user to edit.",
            mapOf("type" to "object", "properties" to emptyMap<String, Any>(), "additionalProperties" to false)),
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
                    "body" to mapOf("type" to "string", "maxLength" to 2000),
                    "title" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 120)
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
            id = "halo_save_screen_to_notebook",
            name = "halo_save_screen_to_notebook",
            description = "Save the redacted semantic screen snapshot for an interaction to Halo Notebook.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "interactionId" to mapOf("type" to "string", "maxLength" to 100)
                ),
                "required" to listOf("interactionId"),
                "additionalProperties" to false
            )
        ),
        ToolDefinition(
            id = "halo_study_with_halo",
            name = "halo_study_with_halo",
            description = "Start a durable guided study session from an exact Workspace source and observed sourceVersion. For notes, first call halo_note_get and use its sourceVersion, NOT its editor version or numeric revision. For other materials use halo_workspace_describe. Stale versions are rejected. No automatic replay of uncertain session creation.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "noteId" to mapOf("type" to "string", "maxLength" to 100, "description" to "Exact Workspace material object ID (legacy parameter name)"),
                    "sourceVersion" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 200)
                ),
                "required" to listOf("noteId", "sourceVersion"),
                "additionalProperties" to false
            )
        ),
        ToolDefinition(
            id = "halo_get_study_session",
            name = "halo_get_study_session",
            description = "Read the state of an existing study session: goal, stage, sources, languages, attempts and measured mastery.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "sessionId" to mapOf("type" to "string", "maxLength" to 100)
                ),
                "required" to listOf("sessionId"),
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
    ) + continuity?.definitions().orEmpty() + canvas?.definitions().orEmpty() + studyActions?.definitions().orEmpty() + noteActions?.definitions().orEmpty() + notePurgeActions?.definitions().orEmpty() + noteAttachmentActions?.definitions().orEmpty() + remoteInk?.definitions().orEmpty()

    override suspend fun execute(toolCall: ToolCall, context: com.halo.runtime.cognition.tools.ToolExecutionContext): ToolExecutionResult {
        remoteInk?.takeIf { adapter -> adapter.definitions().any { it.name == toolCall.functionName } }
            ?.let { return it.execute(toolCall, context) }
        return execute(toolCall)
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        remoteInk?.takeIf { adapter -> adapter.definitions().any { it.name == toolCall.functionName } }?.let { return it.execute(toolCall) }
        continuity?.takeIf { adapter -> adapter.definitions().any { it.name == toolCall.functionName } }?.let { return it.execute(toolCall) }
        canvas?.takeIf { adapter -> adapter.definitions().any { it.name == toolCall.functionName } }?.let { return it.execute(toolCall) }
        studyActions?.takeIf { adapter -> adapter.definitions().any { it.name == toolCall.functionName } }?.let { return it.execute(toolCall) }
        noteActions?.takeIf { adapter -> adapter.definitions().any { it.name == toolCall.functionName } }?.let { return it.execute(toolCall) }
        notePurgeActions?.takeIf { adapter -> adapter.definitions().any { it.name == toolCall.functionName } }?.let { return it.execute(toolCall) }
        noteAttachmentActions?.takeIf { adapter -> adapter.definitions().any { it.name == toolCall.functionName } }?.let { return it.execute(toolCall) }
        fun reject(reason: String, kind: com.halo.runtime.cognition.tools.ToolRejectionKind = com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)

        if (toolCall.functionName == "halo_create_empty_note") {
            if (toolCall.arguments.isNotEmpty()) return reject("Empty note takes no arguments")
            val note = notebookRepository.createNote()
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Created note: ${note.id} - '${note.title}'")
        }
        val key = when (toolCall.functionName) {
            "halo_create_note" -> "body"
            "halo_search_notebook" -> "query"
            "halo_save_screen_to_notebook" -> "interactionId"
            "halo_study_with_halo" -> "noteId"
            "halo_get_study_session" -> "sessionId"
            else -> "objectId"
        }
        val value = toolCall.arguments[key] as? String ?: return reject("Expected $key text")
        val limit = when (key) {
            "body" -> 2000
            "query" -> 500
            else -> 100
        }
        val expectedKeys = when (toolCall.functionName) {
            "halo_study_with_halo" -> setOf(key, "sourceVersion")
            "halo_create_note" -> if ("title" in toolCall.arguments) setOf(key, "title") else setOf(key)
            else -> setOf(key)
        }
        if (toolCall.arguments.keys != expectedKeys || value.length > limit || (key != "query" && value.isBlank())) return reject("Invalid $key")
        if (key == "objectId" || key == "noteId") {
            val item = workspaceRepository.get(WorkspaceObjectId(value.trim())) ?: return reject("Workspace object not found", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
            if (item.lifecycle == com.halo.app.data.notebook.WorkspaceObjectLifecycle.TRASHED) return reject("Workspace object is in trash", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
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
                val title = if ("title" in toolCall.arguments) {
                    val literalTitle = toolCall.arguments["title"] as? String ?: return reject("Expected title text")
                    if (literalTitle.length > 120 || literalTitle.isBlank()) return reject("Invalid title")
                    literalTitle.trim()
                } else null
                val note = notebookRepository.createNote(body, title = title)
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
                val screen = assistantContextRepository?.screenFor(interactionId) ?: return reject("No screen context available for interaction $interactionId", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
                val content = "App: ${screen.packageName}\nActivity: ${screen.activityName}\nTitle: ${screen.title}\nWeb URI: ${screen.webUri}\nNodes:\n" +
                    screen.nodes.take(20).joinToString("\n") { "- ${it.text ?: it.contentDescription}" }
                val note = notebookRepository.createNote(content)
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Saved screen to notebook as note ${note.id}")
            }
            "halo_study_with_halo" -> {
                val objectId = toolCall.arguments["noteId"]?.toString()?.trim().orEmpty()
                if (objectId.isBlank()) return reject("noteId is required")
                val item = workspaceRepository.get(WorkspaceObjectId(objectId))
                    ?: return reject("Notebook note not found", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
                if (item.type !in setOf(com.halo.app.data.notebook.WorkspaceObjectType.NOTE,
                    com.halo.app.data.notebook.WorkspaceObjectType.FILE, com.halo.app.data.notebook.WorkspaceObjectType.MEDIA,
                    com.halo.app.data.notebook.WorkspaceObjectType.ARTIFACT)) {
                    return reject("This Workspace object is not Study material; ${item.type} is unsupported", com.halo.runtime.cognition.tools.ToolRejectionKind.UNSUPPORTED_OPERATION)
                }
                val sourceVersion = (toolCall.arguments["sourceVersion"] as? String)?.takeIf { it.length in 1..200 }
                    ?: return reject("An exact sourceVersion is required")
                if (workspaceContextStore.versionFor(objectId) != sourceVersion) return reject("Study source changed. Describe and select it again.", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
                val starter = exactStudyStarter
                    ?: return reject("Study is unavailable: no exact source session owner is configured", com.halo.runtime.cognition.tools.ToolRejectionKind.CAPABILITY_UNAVAILABLE)
                when (val result = starter(objectId, sourceVersion)) {
                    is StudyStartResult.Started -> ToolExecutionResult.Success(
                        toolCall.id,
                        toolCall.functionName,
                        "Started study session ${result.session.id} for object $objectId in " +
                            "${result.session.tutorStage.name} stage. Source version " +
                            "${result.session.sources.firstOrNull()?.revision ?: "unknown"}. It is saved and can be resumed from Notebook.",
                    )
                    is StudyStartResult.Unavailable ->
                        reject("Study is unavailable: ${result.reason.explanation}", com.halo.runtime.cognition.tools.ToolRejectionKind.CAPABILITY_UNAVAILABLE)
                }
            }
            "halo_get_study_session" -> {
                val sessionId = toolCall.arguments["sessionId"]?.toString()?.trim().orEmpty()
                if (sessionId.isBlank()) return reject("sessionId is required")
                val describe = studySessionDescriptions
                    ?: return reject("Study is unavailable: no durable study session store is configured", com.halo.runtime.cognition.tools.ToolRejectionKind.CAPABILITY_UNAVAILABLE)
                val description = describe(sessionId)
                    ?: return reject("Study session $sessionId was not found", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, description.take(2_000))
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
            else -> reject("Unsupported workspace tool: ${toolCall.functionName}", com.halo.runtime.cognition.tools.ToolRejectionKind.TOOL_UNKNOWN)
        }
    }
}
