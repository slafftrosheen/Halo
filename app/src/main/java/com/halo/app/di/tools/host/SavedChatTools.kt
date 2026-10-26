package com.halo.app.di.tools.host

import com.halo.app.data.SavedChatAdmissionException
import com.halo.app.data.RoomConversationStore
import com.halo.app.data.SavedChatSnapshot
import com.halo.app.data.workspace.WorkspaceActionIds
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import org.json.JSONArray
import org.json.JSONObject

/** Saved-history adapter only. Room owns lifecycle/content; Home owns presentation/continuation. */
class SavedChatTools(private val store: RoomConversationStore, private val canAccess: () -> Boolean,
    private val openDetail: (suspend (String, String) -> Boolean)? = null) : ToolExecutor {
    private val idField = mapOf("type" to "string", "pattern" to WorkspaceActionIds.PATTERN)
    private val versionField = mapOf("type" to "string", "pattern" to "^chat:[a-f0-9]{64}$")
    private val scopeField = mapOf("type" to "string", "enum" to listOf("ACTIVE", "TRASHED"))
    private fun integer(max: Int) = mapOf("type" to "integer", "minimum" to 0, "maximum" to max)
    private fun definition(name: String, description: String, fields: Map<String, Any>, required: List<String>) =
        ToolDefinition(name, name, description + " Local inference, unlocked foreground grown-up access only. No remote history disclosure, account action or automatic replay.",
            mapOf("type" to "object", "properties" to fields, "required" to required, "additionalProperties" to false))
    override fun definitions() = listOf(
        definition(LIST, "List bounded saved chat identities/titles in the explicitly chosen ACTIVE or TRASHED scope; no transcript. Empty unsaved chats are omitted. nextOffset is live-list pagination, not an immutable search snapshot.",
            mapOf("lifecycle" to scopeField, "offset" to integer(10000), "limit" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 20)), listOf("lifecycle")),
        definition(SEARCH, "Search literal text in saved chat titles and turns in the explicitly chosen lifecycle. Return matching identities/titles only, never snippets. This is distinct from current-conversation history search.",
            mapOf("lifecycle" to scopeField, "query" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 120),
                "offset" to integer(10000), "limit" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 20)), listOf("lifecycle", "query")),
        definition(GET, "Read one exact saved chat and its content-derived version, with up to eight bounded turns (1000 characters per speaker). Metadata is never returned. Later pages require the observed version; truncated fields are explicit. Chats over 2048 turns or 2 MB of hashed source require the Saved chats UI.",
            mapOf("conversationId" to idField, "lifecycle" to scopeField, "version" to versionField, "offset" to integer(2048),
                "limit" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 8)), listOf("conversationId", "lifecycle")),
        definition(TRASH, "After explicit approval move one exact observed ACTIVE saved chat to Trash through the conversation owner. Preserves all turns. A running tool cannot trash the active conversation or change its writer. Requires current content version from saved_chat_get. After uncertainty read current lifecycle; never replay.",
            mapOf("conversationId" to idField, "version" to versionField), listOf("conversationId", "version")),
        definition(RESTORE, "After explicit approval restore one exact observed TRASHED saved chat, retaining its turns and active conversation unchanged. Requires current content version. Does not activate, continue or submit the chat.",
            mapOf("conversationId" to idField, "version" to versionField), listOf("conversationId", "version")),
        definition(OPEN, "Request the existing detail page of an exact ACTIVE saved chat for review and its human Continue control. DISPATCHED does not mean rendered or continued. Does not switch the writer, cancel the current run, submit a message or attach history. Direct continuation during a running tool is intentionally unavailable.",
            mapOf("conversationId" to idField, "version" to versionField), listOf("conversationId", "version")),
    )

    override suspend fun execute(toolCall: ToolCall) = execute(toolCall, ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        fun refuse(kind: ToolRejectionKind = ToolRejectionKind.RESOURCE_UNAVAILABLE) = ToolExecutionResult.Rejected(
            toolCall.id, toolCall.functionName, "Saved chat scope, source version or local access is unavailable. Read the exact current chat before asking to change it.", kind)
        val definition = definitions().firstOrNull { it.name == toolCall.functionName } ?: return refuse(ToolRejectionKind.TOOL_UNKNOWN)
        val scope = currentCoroutineContext()[RunToolScope] ?: return refuse(ToolRejectionKind.PERMISSION_REQUIRED)
        fun admit() { check(scope.targetIsLocal && canAccess()) { "Saved chat access denied" } }
        if (!scope.targetIsLocal || !canAccess()) return refuse(ToolRejectionKind.PERMISSION_REQUIRED)
        val mutation = toolCall.functionName in setOf(TRASH, RESTORE)
        if (mutation && !context.directUserAuthority) return refuse(ToolRejectionKind.PERMISSION_REQUIRED)
        var effectStarted = false
        try {
            val args = toolCall.arguments
            val fields = definition.parameters["properties"] as Map<*, *>
            val required = definition.parameters["required"] as List<*>
            require(args.keys.all { it in fields } && args.keys.containsAll(required.filterIsInstance<String>()))
            fun number(key: String, default: Int, range: IntRange): Int {
                if (key !in args) return default
                val number = args[key] as? Number ?: throw IllegalArgumentException()
                val value = number.toDouble()
                require(value.isFinite() && value == value.toInt().toDouble() && value.toInt() in range)
                return value.toInt()
            }
            fun id() = (args["conversationId"] as? String)?.takeIf(WorkspaceActionIds::isValid) ?: throw IllegalArgumentException()
            fun version(required: Boolean = false): String? {
                val value = args["version"]
                if (value == null && !required && "version" !in args) return null
                return (value as? String)?.takeIf { it.matches(Regex("chat:[a-f0-9]{64}")) } ?: throw IllegalArgumentException()
            }
            fun lifecycle() = (args["lifecycle"] as? String)?.takeIf { it in setOf("ACTIVE", "TRASHED") } ?: throw IllegalArgumentException()
            val output = JSONObject().put("schemaVersion", 1).put("trust", "REFERENCE_DATA")
            var truth = ActionTruth.ACKNOWLEDGED
            when (toolCall.functionName) {
                LIST, SEARCH -> {
                    val query = if (toolCall.functionName == SEARCH) (args["query"] as? String)?.takeIf { it.isNotBlank() && it.length <= 120 }
                        ?: throw IllegalArgumentException() else ""
                    val page = store.listSavedChats(lifecycle(), query, number("offset", 0, 0..10000), number("limit", 10, 1..20), ::admit)
                    output.put("chats", JSONArray().apply { page.chats.forEach {
                        put(JSONObject().put("conversationId", it.id).put("title", it.title).put("lifecycle", it.lifecycle).put("updatedAt", it.updatedAt))
                    } }).put("nextOffset", page.nextOffset ?: JSONObject.NULL).put("state", "READ")
                }
                GET -> {
                    val saved = store.savedChat(id(), lifecycle(), version(), number("offset", 0, 0..2048), number("limit", 5, 1..8), ::admit)
                    snapshot(output, saved, true).put("state", "READ")
                }
                TRASH, RESTORE -> {
                    val id = id(); val version = requireNotNull(version(true))
                    // Exact source admission occurs again inside Room's mutation transaction.
                    admit(); effectStarted = true
                    val changed = if (toolCall.functionName == TRASH) store.trashSavedConversation(id, version, ::admit)
                        else store.restoreSavedConversation(id, version, ::admit)
                    if (!changed) return refuse()
                    val saved = store.savedChat(id, if (toolCall.functionName == TRASH) "TRASHED" else "ACTIVE", limit = 1, admit = ::admit)
                    snapshot(output, saved, false).put("sourceVersion", version).put("state", "SAVED").put("activeConversationChanged", false)
                    truth = ActionTruth.CONFIRMED
                }
                OPEN -> {
                    val id = id(); val version = requireNotNull(version(true))
                    store.savedChat(id, "ACTIVE", version, limit = 1, admit = ::admit)
                    val open = openDetail ?: return refuse(ToolRejectionKind.CAPABILITY_UNAVAILABLE)
                    admit(); effectStarted = true
                    check(open(id, version))
                    output.put("conversationId", id).put("version", version).put("state", "DISPATCHED").put("continued", false)
                    truth = ActionTruth.DISPATCHED
                }
            }
            admit()
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, output.toString(), truth)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SavedChatAdmissionException) { return refuse() }
        catch (_: IllegalArgumentException) {
            return if (effectStarted) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
                "Saved chat outcome is unconfirmed. Read current lifecycle/version; never replay automatically.",
                completionUnknown = true, failureKind = ToolFailureKind.UNKNOWN) else refuse(ToolRejectionKind.INVALID_ARGUMENTS)
        }
        catch (_: Exception) {
            return if (effectStarted) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
                "Saved chat outcome is unconfirmed. Read its current lifecycle/version before any further action; never replay automatically.",
                completionUnknown = true, failureKind = ToolFailureKind.UNKNOWN) else refuse()
        }
    }

    private fun snapshot(output: JSONObject, value: SavedChatSnapshot, includeTurns: Boolean): JSONObject {
        output.put("conversationId", value.id).put("title", value.title).put("lifecycle", value.lifecycle)
            .put("version", value.version).put("updatedAt", value.updatedAt).put("totalTurns", value.totalTurns)
        if (includeTurns) output.put("turns", JSONArray().apply { value.turns.forEach {
            put(JSONObject().put("turnId", it.id).put("timestamp", it.timestamp).put("userText", it.userText)
                .put("assistantText", it.assistantText).put("truncated", it.truncated))
        } }).put("nextOffset", value.nextOffset ?: JSONObject.NULL)
        return output
    }

    companion object {
        const val LIST = "halo_saved_chats_list"
        const val SEARCH = "halo_saved_chats_search"
        const val GET = "halo_saved_chat_get"
        const val TRASH = "halo_saved_chat_trash"
        const val RESTORE = "halo_saved_chat_restore"
        const val OPEN = "halo_saved_chat_open"
        val names = setOf(LIST, SEARCH, GET, TRASH, RESTORE, OPEN)
        fun operability(name: String) = ToolOperability(version = 1, capabilityId = CapabilityId("host.saved_chats"),
            owner = "RoomConversationStore/Workspace lifecycle; HomeViewModel navigation", operation = when (name) {
                TRASH, RESTORE -> ToolOperation.EDIT; OPEN -> ToolOperation.PRESENT; else -> ToolOperation.QUERY },
            resultEncoding = ToolResultEncoding.JSON,
            sourceVersionArguments = if (name in setOf(GET, TRASH, RESTORE, OPEN)) setOf("version") else emptySet(),
            resultIdFields = if (name in setOf(LIST, SEARCH)) emptySet() else setOf("conversationId", "version"),
            resultSchema = mapOf("type" to "object", "properties" to mapOf(
                "schemaVersion" to mapOf("type" to "integer", "enum" to listOf(1)),
                "conversationId" to mapOf("type" to "string", "maxLength" to 100),
                "version" to mapOf("type" to "string", "pattern" to "^chat:[a-f0-9]{64}$"),
                "state" to mapOf("type" to "string", "enum" to listOf("READ", "SAVED", "DISPATCHED"))),
                "required" to listOf("schemaVersion", "state")),
            limitations = setOf("Explicit lifecycle; local unlocked foreground grown-up reads only. Remote history disclosure is unavailable.",
                "Exact content-derived versions; max 2048 turns/2 MB source, eight turns per page and 1000 characters per speaker. Metadata omitted.",
                "Trash refuses active writer. Mutations require explicit current authority and original confirmation; no replay after uncertainty.",
                "Open only dispatches saved detail; direct active-session continuation is unavailable. Human Continue uses the original owner."))
    }
}
