package com.halo.app.di.tools

import com.halo.app.data.workspace.*
import com.halo.app.everyday.EverydayWriteAuthority
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import org.json.JSONObject

class RichNoteCopyService(private val text: WorkspaceTextDocuments, private val authority: () -> (() -> Unit)) {
    suspend fun copy(id: String, revision: Long): WorkspaceRichCopy {
        val check = authority(); check()
        return EverydayWriteAuthority.run(check) { text.richCopy(id, revision) }.also { check() }
    }
}
object RichNoteCopyContract {
    private val string = mapOf("type" to "string", "maxLength" to 200)
    private val version = mapOf("type" to "integer", "minimum" to 1)
    val definition = ToolDefinition("halo_text_rich_copy", "halo_text_rich_copy",
        "Make a separate rich block-note copy of an exact saved literal TXT/Markdown/CSV document. Preserves original bytes and source revision. Markdown is parsed by the existing note codec. Returns original/new IDs and revisions; never replay an uncertain creation.",
        mapOf("type" to "object", "properties" to mapOf("noteId" to string, "revision" to version), "required" to listOf("noteId", "revision"), "additionalProperties" to false))
    fun operability(value: ToolDefinition): ToolOperability? = if (value.name != definition.name) null else ToolOperability(
        capabilityId = CapabilityId("workspace.write"), owner = "WorkspaceTextDocuments / NotebookDocumentRepository", operation = ToolOperation.CREATE,
        resultEncoding = ToolResultEncoding.JSON, sourceVersionArguments = setOf("revision"), resultIdFields = setOf("noteId", "sourceId"),
        resultSchema = mapOf("type" to "object", "properties" to mapOf("noteId" to string, "revision" to version, "sourceId" to string, "sourceRevision" to version, "created" to mapOf("type" to "boolean")),
            "required" to listOf("noteId", "revision", "sourceId", "sourceRevision", "created"), "additionalProperties" to false),
        limitations = setOf("Explicit adult local copy, no provider. The original is unchanged. UI and typed/voice-final share the same owner."))
}
class RichNoteCopyTools(private val owner: RichNoteCopyService) : ToolExecutor {
    override fun definitions() = listOf(RichNoteCopyContract.definition)
    override suspend fun execute(toolCall: ToolCall) = execute(toolCall, ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult = withContext(Dispatchers.IO) {
        var entered = false
        try {
            require(toolCall.functionName == RichNoteCopyContract.definition.name && !context.interactionId.isNullOrBlank())
            EverydayToolContracts.validate(toolCall.arguments, RichNoteCopyContract.definition.parameters)
            entered = true
            val receipt = owner.copy(toolCall.arguments["noteId"] as String, (toolCall.arguments["revision"] as Number).toLong())
            val result = JSONObject().put("noteId", receipt.noteId).put("revision", receipt.revision).put("sourceId", receipt.sourceId).put("sourceRevision", receipt.sourceRevision).put("created", true)
            ToolExecutionResult.Success(toolCall.id, toolCall.functionName, result.toString())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (entered) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName, "Copy receipt is uncertain; read the current Workspace before continuing. Never repeat this creation automatically.", completionUnknown = true, code = "RICH_COPY_UNCERTAIN")
            else ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Read the exact saved document revision and request an explicit copy", ToolRejectionKind.INVALID_ARGUMENTS)
        }
    }
}
