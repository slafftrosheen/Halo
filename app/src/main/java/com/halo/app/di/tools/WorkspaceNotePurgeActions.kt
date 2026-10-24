package com.halo.app.di.tools

import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.WorkspaceActionIds
import com.halo.app.notebook.NotebookUseCases
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Deletion has its own commit/outcome boundary; never wrap this adapter in a note transaction. */
class WorkspaceNotePurgeActions(private val notes: NotebookUseCases) : ToolExecutor {
    private val id = mapOf("type" to "string", "pattern" to WorkspaceActionIds.PATTERN)
    private val note = mapOf("noteId" to id)
    private fun identity(pattern: String) = mapOf("type" to "string", "pattern" to pattern)
    private fun definition(name: String, description: String, fields: Map<String, Any>, required: Set<String> = fields.keys) =
        ToolDefinition(name, name, description, mapOf("type" to "object", "properties" to fields, "required" to required.toList(), "additionalProperties" to false))
    override fun definitions() = listOf(
        definition("halo_note_purge_status", "Read the durable permanent-deletion receipt and exact receiptVersion for a note ID. NOT_FOUND means no known receipt, never assumed success. Distinguish original deletion, source restored, pending/review cleanup, exclusive asset entries cleared and assets retained for other owners; no filesystem mutation.", note),
        definition("halo_note_purge", "After explicit permanent-deletion approval remove exactly the observed trashed Notebook note through Notebook's owner. Read halo_note_get first. For a restored note with an earlier purge receipt, pass that read's purgeReceiptVersion as priorReceiptVersion too. Linked original sources remain. If deleting its pending recovery draft is explicitly requested, pass includeRecoveryDraft=true and the exact recoveryVersion. Saves a durable receipt; note deletion and file cleanup are separate facts. Shared and externally owned files remain. Never automatically replay; query purge_status after uncertainty.",
            note + mapOf("version" to identity("^note:[a-f0-9]{64}$"), "includeRecoveryDraft" to mapOf("type" to "boolean"), "recoveryVersion" to identity("^note-draft:(none|[a-f0-9]{64})$"), "priorReceiptVersion" to identity("^note-purge:[a-f0-9]{64}$")), note.keys + "version"),
        definition("halo_note_purge_retry", "After approval retry only cleanup from an exact durable Notebook deletion receiptVersion. Never targets a new/restored note, changed asset metadata, arbitrary paths or external/shared source bytes. A stale receipt requires another read; no automatic replay.",
            note + ("receiptVersion" to identity("^note-purge:[a-f0-9]{64}$"))),
    )
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun refuse(reason: String, kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        val definition = definitions().firstOrNull { it.name == toolCall.functionName } ?: return refuse("Purge action unavailable", ToolRejectionKind.UNSUPPORTED_OPERATION)
        try {
            val args = toolCall.arguments
            val fields = definition.parameters["properties"] as Map<*, *>
            require(args.keys.all { it in fields } && args.keys.containsAll((definition.parameters["required"] as List<*>).filterIsInstance<String>()))
            val id = (args["noteId"] as? String)?.takeIf(WorkspaceActionIds::isValid) ?: throw IllegalArgumentException()
            fun identity(key: String, regex: String) = (args[key] as? String ?: throw IllegalArgumentException()).also { require(it.matches(Regex(regex))) }
            if (toolCall.functionName == "halo_note_purge_status") {
                val saved = notes.purgeStatus(id)
                return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, saved?.let(::receipt)?.toString()
                    ?: JSONObject().put("noteId", id).put("status", "NOT_FOUND").put("originalDeletionRecorded", false).toString())
            }
            val result = if (toolCall.functionName == "halo_note_purge_retry") notes.retryPurge(id, identity("receiptVersion", "note-purge:[a-f0-9]{64}"))
                else {
                    val includeRecovery = if ("includeRecoveryDraft" in args) args["includeRecoveryDraft"] as? Boolean ?: throw IllegalArgumentException() else false
                    val recovery = if ("recoveryVersion" in args) identity("recoveryVersion", "note-draft:(none|[a-f0-9]{64})") else null
                    require(!includeRecovery || recovery != null)
                    val prior = if ("priorReceiptVersion" in args) identity("priorReceiptVersion", "note-purge:[a-f0-9]{64}") else null
                    notes.purgeNote(id, identity("version", "note:[a-f0-9]{64}"), recovery, includeRecovery, prior)
                }
            return when (result) {
                is NotebookPurgeResult.Saved -> ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                    receipt(result.receipt).put("alreadyRecorded", result.alreadyRecorded).toString())
                is NotebookPurgeResult.Refused -> refuse("Deletion refused (${result.reason}). Read the note or purge receipt before asking to continue.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) { return refuse("Use exact saved note/draft/receipt identities and explicit deletion choices") }
        catch (_: Exception) { return refuse("Deletion outcome could not be confirmed. Read halo_note_purge_status; never replay an uncertain deletion.", ToolRejectionKind.RESOURCE_UNAVAILABLE) }
    }
    private fun receipt(receipt: NotebookPurgeReceipt): JSONObject = receipt.record.let { row -> JSONObject()
        .put("noteId", row.noteId).put("receiptVersion", receipt.version).put("sourceVersion", row.sourceVersion)
        .put("status", row.state).put("originalDeletionRecorded", true).put("deleted", !receipt.sourcePresent)
        .put("sourcePresent", receipt.sourcePresent).put("cleanupComplete", row.state == "COMPLETE" && !receipt.sourcePresent)
        .put("pendingAssets", row.pending).put("exclusiveAssetsCleared", row.exclusiveCleared)
        .put("sharedAssetsRetained", row.sharedRetained).put("externalAssetsRetained", row.externalRetained)
        .put("reason", row.reason ?: JSONObject.NULL) }
}
