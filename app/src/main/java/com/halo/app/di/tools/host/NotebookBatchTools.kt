package com.halo.app.di.tools.host

import com.halo.app.ui.screens.notebook.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Typed adapter for the exact same suspend owner used by Library's selection toolbar and Undo. */
class NotebookBatchTools(private val session: NotebookLibrarySession) : ToolExecutor {
    override fun definitions() = listOf(
        ToolDefinition(READ, READ,
            "Prepare an exact selected Library batch (CONTEXT, COLLECTION, ARCHIVE, TRASH, RESTORE), or UNDO_TRASH for only the last batch's successfully trashed identities. Requires the fresh settled foreground Library snapshot. Returns per-object canonical versions and a one-use batch token; returns no titles/body and makes no content attachment or lifecycle change. COLLECTION requires an existing collectionId. Undo targets are independent of current selection. Local foreground grown-up inference only.",
            schema(mapOf("expectedSnapshot" to token, "operation" to operation, "collectionId" to id), listOf("expectedSnapshot", "operation"))),
        ToolDefinition(WRITE, WRITE,
            "After explicit current user authority and runtime approval, execute exactly the operation in a freshly prepared one-use halo_library_batch token. Operates only on that selection or exact last-Trash Undo set, never whatever is selected later. Uses canonical versions, lifecycle, saved-chat writer guards and existing context/membership owners. CONTEXT is explicit source selection and retains all existing context budget and sharing protections; it grants no remote disclosure. Partial unchanged and unconfirmed outcomes are separate from successful IDs. Any unconfirmed outcome requires fresh readback and a new user decision; never replay. No permanent purge.",
            schema(mapOf("expectedSnapshot" to token, "expectedBatch" to token, "operation" to operation),
                listOf("expectedSnapshot", "expectedBatch", "operation"))),
    )

    override suspend fun execute(toolCall: ToolCall) = execute(toolCall, ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        if (toolCall.functionName !in NAMES) return reject(toolCall, ToolRejectionKind.TOOL_UNKNOWN)
        if (currentCoroutineContext()[RunToolScope]?.targetIsLocal != true) return reject(toolCall, ToolRejectionKind.PERMISSION_REQUIRED)
        if (toolCall.functionName == WRITE && (!context.directUserAuthority || context.interactionId.isNullOrBlank()))
            return reject(toolCall, ToolRejectionKind.PERMISSION_REQUIRED)
        val args = toolCall.arguments
        val snapshot = (args["expectedSnapshot"] as? String)?.takeIf { it.length in 1..100 }
            ?: return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
        val rawOperation = args["operation"] as? String ?: return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
        val undo = rawOperation == "UNDO_TRASH"
        val action = if (undo) NotebookBatchAction.RESTORE else NotebookBatchAction.entries.firstOrNull { it.name == rawOperation }
            ?: return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
        val collectionId = args["collectionId"] as? String
        val expectedKeys = if (toolCall.functionName == READ)
            setOf("expectedSnapshot", "operation") + if (action == NotebookBatchAction.COLLECTION) setOf("collectionId") else emptySet()
        else setOf("expectedSnapshot", "expectedBatch", "operation")
        if (args.keys != expectedKeys || (toolCall.functionName == READ && action == NotebookBatchAction.COLLECTION &&
                (collectionId == null || collectionId.length !in 1..240))) return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
        val batch = if (toolCall.functionName == WRITE) (args["expectedBatch"] as? String)?.takeIf { it.length in 1..100 }
            ?: return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS) else null
        return withContext(Dispatchers.Main.immediate) {
            if (toolCall.functionName == READ) {
                try {
                    val prepared = session.prepareBatch(snapshot, action, collectionId, undo) ?: return@withContext reject(toolCall)
                    val output = JSONObject().put("schemaVersion", 1).put("snapshot", snapshot)
                        .put("batchSnapshot", prepared.token).put("operation", rawOperation)
                        .put("collectionId", prepared.admission.collectionId ?: JSONObject.NULL)
                        .put("unavailableIds", JSONArray(prepared.admission.unavailableIds.toList()))
                        .put("objects", JSONArray().apply { prepared.admission.sources.forEach {
                            put(JSONObject().put("objectId", it.objectId).put("version", it.version).put("lifecycle", it.lifecycle.name))
                        } }).put("contentReturned", false).put("applied", false)
                    ToolExecutionResult.Success(toolCall.id, READ, output.toString(), ActionTruth.ACKNOWLEDGED)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { reject(toolCall) }
            } else {
                val disclosure = session.readbackLease() ?: return@withContext reject(toolCall)
                try {
                    val result = session.applyBatch(snapshot, requireNotNull(batch), action, undo) ?: return@withContext reject(toolCall)
                    if (!session.canReadback(disclosure)) return@withContext uncertain(toolCall)
                    val output = JSONObject().put("schemaVersion", 1).put("operation", rawOperation)
                        .put("succeededIds", JSONArray(result.succeeded.toList())).put("unchangedIds", JSONArray(result.failed.toList()))
                        .put("unconfirmedIds", JSONArray(result.unknown.toList())).put("undoAvailable", result.undoAvailable)
                        .put("state", if (result.unknown.isNotEmpty()) "PARTIAL_UNCONFIRMED" else if (result.failed.isNotEmpty()) "PARTIAL" else "APPLIED")
                    if (result.unknown.isNotEmpty()) return@withContext ToolExecutionResult.Failed(
                        toolCall.id, WRITE, "Some selected Library outcomes are unconfirmed. Read current state and obtain a fresh user decision; never replay.",
                        completionUnknown = true, code = "LIBRARY_BATCH_PARTIAL_UNCONFIRMED",
                        payload = output.put("doNotReplay", true).toString())
                    // ACKNOWLEDGED deliberately avoids claiming complete success for any partial batch.
                    ToolExecutionResult.Success(toolCall.id, WRITE, output.toString(),
                        if (result.failed.isEmpty() && result.unknown.isEmpty()) ActionTruth.CONFIRMED else ActionTruth.ACKNOWLEDGED)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { uncertain(toolCall) }
            }
        }
    }

    private fun reject(call: ToolCall, kind: ToolRejectionKind = ToolRejectionKind.RESOURCE_UNAVAILABLE) =
        ToolExecutionResult.Rejected(call.id, call.functionName,
            "Library batch is unavailable, stale or not permitted. Read the current local view and prepare a fresh batch before another request.", kind)
    private fun uncertain(call: ToolCall) = ToolExecutionResult.Failed(call.id, call.functionName,
        "Library batch outcome cannot be confirmed or disclosed. Never replay. Return to an authorized Library, inspect current state and obtain a fresh user decision.",
        completionUnknown = true, code = "LIBRARY_BATCH_UNCERTAIN")

    companion object {
        const val READ = "halo_library_batch"
        const val WRITE = "halo_library_apply_batch"
        val NAMES = setOf(READ, WRITE)
        private val token = mapOf("type" to "string", "minLength" to 1, "maxLength" to 100)
        private val id = mapOf("type" to "string", "minLength" to 1, "maxLength" to 240)
        private val operation = mapOf("type" to "string", "enum" to NotebookBatchAction.entries.map { it.name } + "UNDO_TRASH")
        private fun schema(properties: Map<String, Any>, required: List<String>) = mapOf("type" to "object", "properties" to properties,
            "required" to required, "additionalProperties" to false)
        fun descriptor(definition: ToolDefinition): ToolDescriptor? {
            if (definition.name !in NAMES) return null
            return ToolDescriptor(ToolId(definition.name), definition, ToolCategory.WORKSPACE,
                keywords = setOf("library", "selection", "context", "collection", "archive", "trash", "restore", "undo"),
                requirements = setOf(ToolRequirement.Capability(operability(definition.name).capabilityId)),
                security = if (definition.name == READ) ToolSecurityPolicy.READ_ONLY else ToolSecurityPolicy(
                    risk = ToolRiskClass.DESTRUCTIVE, confirmation = ConfirmationPolicy.ALWAYS, replaySafety = ReplaySafety.NEVER_REPLAY),
                execution = ToolExecutionPolicy(maxOutputChars = 128000),
                presentation = ToolPresentation("Library selected items", actionVerb = "Change"), operability = operability(definition.name))
        }
        fun operability(name: String) = ToolOperability(version = 1, capabilityId = CapabilityId("host.assistant.library_batch"),
            owner = "NotebookViewModel/NotebookLibraryBatchOwner/RoomConversationStore/Room", operation = if (name == READ) ToolOperation.QUERY else ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON,
            sourceVersionArguments = if (name == READ) setOf("expectedSnapshot") else setOf("expectedSnapshot", "expectedBatch"),
            resultIdFields = emptySet(),
            resultSchema = mapOf("type" to "object", "properties" to mapOf(
                "schemaVersion" to mapOf("type" to "integer", "enum" to listOf(1)), "operation" to operation,
                "batchSnapshot" to token, "objects" to mapOf("type" to "array"),
                "unavailableIds" to mapOf("type" to "array"),
                "succeededIds" to mapOf("type" to "array"), "unchangedIds" to mapOf("type" to "array"),
                "unconfirmedIds" to mapOf("type" to "array"), "undoAvailable" to mapOf("type" to "boolean"),
                "state" to mapOf("type" to "string", "enum" to listOf("APPLIED", "PARTIAL", "PARTIAL_UNCONFIRMED"))),
                "required" to listOf("schemaVersion", "operation")),
            limitations = setOf("Only the same resumed, settled, unlocked grown-up local Library and exact selection revision.",
                "One-use source admission, direct authority and ALWAYS approval; NEVER_REPLAY, including after partial/unknown outcomes.",
                "Saved-chat lifecycle refuses the active writer. Undo contains only exact successfully committed last-Trash versions.",
                "No titles/body, permanent purge, remote disclosure or context-policy grant. Source and collection checks stay with existing Room owners."))
    }
}
