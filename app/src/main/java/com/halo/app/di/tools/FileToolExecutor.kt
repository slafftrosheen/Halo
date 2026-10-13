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
        definition("halo_read_text_file", "Read the first 3500 characters of an imported UTF-8 text document. Binary documents are not supported.", "objectId"),
        ToolDefinition("halo_file_handoff", "halo_file_handoff", "View or share imported files through Android. Dispatch does not confirm delivery.",
            mapOf("type" to "object", "properties" to mapOf(
                "action" to mapOf("type" to "string", "enum" to listOf("view", "edit", "share", "export", "print", "install_apk")),
                "objectIds" to mapOf("type" to "array", "minItems" to 1, "maxItems" to 16, "items" to mapOf("type" to "string"))),
                "required" to listOf("action", "objectIds"), "additionalProperties" to false))
    )
    private fun definition(id: String, description: String, key: String) = ToolDefinition(id, id, description,
        mapOf("type" to "object", "properties" to mapOf(key to mapOf("type" to "string", "maxLength" to 500)),
            "required" to listOf(key), "additionalProperties" to false))

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = withContext(Dispatchers.IO) {
        fun reject(message: String, kind: com.halo.runtime.cognition.tools.ToolRejectionKind = com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, message, kind)
        fun success(message: String) = ToolExecutionResult.Success(toolCall.id, toolCall.functionName, message.take(4000))
        if (toolCall.functionName == "halo_file_handoff") return@withContext handoff(toolCall)
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
    private suspend fun handoff(call: ToolCall): ToolExecutionResult {
        fun reject(message: String, kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(call.id, call.functionName, message, kind)
        val action = call.arguments["action"] as? String
        val ids = call.arguments["objectIds"] as? List<*> ?: return reject("Select imported object IDs")
        if (call.arguments.keys != setOf("action", "objectIds") || action !in setOf("view", "edit", "share", "export", "print", "install_apk") ||
            ids.size !in 1..16 || ids.any { it !is String || it.isBlank() || it.length > 500 } ||
            ids.distinct().size != ids.size || (action != "share" && ids.size != 1)) return reject("Invalid action or object IDs")
        val selected = mutableListOf<WorkspaceFile>()
        for (value in ids) {
            val id = WorkspaceObjectId(value as String)
            val item = workspace.get(id) ?: return reject("Object missing", ToolRejectionKind.RESOURCE_UNAVAILABLE)
            if (item.lifecycle == WorkspaceObjectLifecycle.TRASHED) return reject("Object is in trash", ToolRejectionKind.RESOURCE_UNAVAILABLE)
            val file = files.getFile(id) ?: return reject("Imported file missing", ToolRejectionKind.RESOURCE_UNAVAILABLE)
            if (file.uri.scheme != "content" || file.availability !in setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL))
                return reject("Relink the file in Notebook", ToolRejectionKind.RESOURCE_UNAVAILABLE)
            if (action == "edit" && file.availability != WorkspaceFileAvailability.OWNED_LOCAL)
                return reject("Copy the imported file into Halo before granting editing access", ToolRejectionKind.PERMISSION_REQUIRED)
            selected += file
        }
        val uris = selected.map { it.uri }
        val mime = selected.map { it.mimeType }.distinct().singleOrNull() ?: "*/*"
        if (action == "install_apk" && (mime != "application/vnd.android.package-archive" || selected.single().availability != WorkspaceFileAvailability.OWNED_LOCAL))
            return reject("Choose an imported Halo-owned APK; Android owns validation and installation review", ToolRejectionKind.INVALID_ARGUMENTS)
        if (action == "install_apk" && !context.packageManager.canRequestPackageInstalls())
            return reject("Allow Halo in Android unknown-source settings, then review installation again", ToolRejectionKind.PERMISSION_REQUIRED)
        if (action in setOf("export", "print")) {
            if (action == "print" && mime != "application/pdf") return reject("Choose an imported PDF to print", ToolRejectionKind.UNSUPPORTED_OPERATION)
            if ((selected.single().sizeBytes ?: 0) > 64L * 1024 * 1024) return reject("Export limit is 64 MB", ToolRejectionKind.INVALID_ARGUMENTS)
            val (requestId, _) = com.halo.app.nativeactions.NativeActivityRequests.create()
            return try {
                context.startActivity(android.content.Intent(context, com.halo.app.nativeactions.NativeFileActionActivity::class.java)
                    .putExtra("requestId", requestId).putExtra("sourceUri", uris.single().toString())
                    .putExtra("title", selected.single().displayName).putExtra("mime", mime).putExtra("action", action)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                ToolExecutionResult.Success(call.id, call.functionName,
                    ToolResultEnvelope.success(com.halo.runtime.cognition.agent.ActionTruth.DISPATCHED,
                        details = "$action requested; requestId=$requestId; use halo_native_result for observed result"), com.halo.runtime.cognition.agent.ActionTruth.DISPATCHED)
            } catch (_: android.content.ActivityNotFoundException) {
                com.halo.app.nativeactions.NativeActivityRequests.cancel(requestId)
                reject("File activity unavailable", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
            }
        }
        val intent = when {
            action in setOf("view", "edit", "install_apk") -> android.content.Intent(if (action == "edit") android.content.Intent.ACTION_EDIT else android.content.Intent.ACTION_VIEW).setDataAndType(uris.single(), mime)
            uris.size == 1 -> android.content.Intent(android.content.Intent.ACTION_SEND).setType(mime).putExtra(android.content.Intent.EXTRA_STREAM, uris.single())
            else -> android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).setType(mime)
                .putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.clipData = android.content.ClipData.newRawUri("Selected Halo files", uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
        }
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (action == "edit") intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        return try {
            context.startActivity((if (action == "share") android.content.Intent.createChooser(intent, null) else intent)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolExecutionResult.Success(call.id, call.functionName,
                ToolResultEnvelope.success(com.halo.runtime.cognition.agent.ActionTruth.DISPATCHED,
                    details = "$action requested for ${uris.size} imported files; delivery not verified"), com.halo.runtime.cognition.agent.ActionTruth.DISPATCHED)
        } catch (_: android.content.ActivityNotFoundException) { reject("No file handler", ToolRejectionKind.CAPABILITY_UNAVAILABLE) }
        catch (_: SecurityException) { reject("File permission expired", ToolRejectionKind.PERMISSION_REQUIRED) }
    }
}
