package com.halo.app.di.tools

import android.content.Context
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.workspace.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Reads only files explicitly imported into the workspace, never arbitrary paths or URLs. */
class FileToolExecutor(private val context: Context, private val files: WorkspaceFileRepository,
    private val workspace: WorkspaceRepository) : ToolExecutor {
    override fun definitions() = listOf(
        definition("halo_search_files", "Find imported files by name; returns workspace IDs.", "query"),
        definition("halo_get_file_metadata", "Read metadata for an imported file ID.", "objectId"),
        definition("halo_read_text_file", "Read the first 3500 characters of an imported UTF-8 text document. Binary documents are not supported.", "objectId")
    )
    private fun definition(id: String, description: String, key: String) = ToolDefinition(id, id, description,
        mapOf("type" to "object", "properties" to mapOf(key to mapOf("type" to "string", "maxLength" to 500)),
            "required" to listOf(key), "additionalProperties" to false))

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = withContext(Dispatchers.IO) {
        fun reject(message: String, kind: com.halo.runtime.cognition.tools.ToolRejectionKind = com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, message, kind)
        fun success(message: String) = ToolExecutionResult.Success(toolCall.id, toolCall.functionName, message.take(4000))
        val key = if (toolCall.functionName == "halo_search_files") "query" else "objectId"
        val value = (toolCall.arguments[key] as? String)?.trim() ?: return@withContext reject("Expected $key")
        if (toolCall.arguments.keys != setOf(key) || value.length > 500) return@withContext reject("Invalid arguments")
        if (toolCall.functionName == "halo_search_files") {
            val active = workspace.observe().first().map { it.id }.toSet()
            return@withContext success(files.observeFiles().first()
                .filter { it.objectId in active && it.displayName.contains(value, ignoreCase = true) }.take(12)
                .joinToString("\n") { "[${it.objectId.value}] ${it.displayName} (${it.mimeType}, ${it.availability})" }
                .ifEmpty { "No matching imported files" })
        }
        val id = WorkspaceObjectId(value)
        val item = workspace.get(id) ?: return@withContext reject("Workspace object not found", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
        if (item.lifecycle == WorkspaceObjectLifecycle.TRASHED) return@withContext reject("File is in trash", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
        val file = files.getFile(id) ?: return@withContext reject("Imported file not found", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
        if (toolCall.functionName == "halo_get_file_metadata") return@withContext success(
            "[${id.value}] ${file.displayName}\nType: ${file.mimeType}\nBytes: ${file.sizeBytes ?: "unknown"}\nAvailability: ${file.availability}")
        if (toolCall.functionName != "halo_read_text_file") return@withContext reject("Unsupported file operation", com.halo.runtime.cognition.tools.ToolRejectionKind.TOOL_UNKNOWN)
        if (file.availability !in setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL))
            return@withContext reject("File unavailable; relink it in Notebook", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
        if (!file.mimeType.startsWith("text/") && file.mimeType !in setOf("application/json", "application/xml", "application/javascript"))
            return@withContext reject("This file is not a supported text document", com.halo.runtime.cognition.tools.ToolRejectionKind.UNSUPPORTED_OPERATION)
        try {
            val input = context.contentResolver.openInputStream(file.uri) ?: return@withContext reject("File cannot be opened", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
            val text = input.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(3501)
                var count = 0
                while (count < buffer.size) {
                    val read = reader.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                String(buffer, 0, minOf(count, 3500)) + if (count > 3500) "\n[Truncated]" else ""
            }
            success(text)
        } catch (error: SecurityException) {
            reject("Document access expired; relink it in Notebook", com.halo.runtime.cognition.tools.ToolRejectionKind.PERMISSION_REQUIRED)
        } catch (error: java.io.IOException) {
            ToolExecutionResult.Failed(toolCall.id, toolCall.functionName, error.message ?: "Unable to read imported file")
        }
    }
}
