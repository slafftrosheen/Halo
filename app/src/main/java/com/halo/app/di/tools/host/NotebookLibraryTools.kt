package com.halo.app.di.tools.host

import com.halo.app.ui.screens.notebook.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Explicit local access to the resumed Library's real VM, not a navigation/callback dispatcher. */
class NotebookLibraryTools(private val session: NotebookLibrarySession) : ToolExecutor {
    override fun definitions() = listOf(
        ToolDefinition(READ, READ, "Read the actual foreground grown-up Library view locally. Returns requested and rendered query/filter/sort, collection IDs, selected IDs and paginated visible row identifiers with favorite/lifecycle metadata. No document titles, snippets, content, selection for assistant context or remote disclosure. settled=false means the owner's debounced view is still loading. Snapshot is an ephemeral view lease, not a document source version.",
            schema(mapOf("expectedSnapshot" to token, "offset" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 100000)))),
        ToolDefinition(SET, SET, "Change exactly one actual Library view setting: query, filter, sort, collection filter, or explicit visible selection. Requires a fresh settled halo_library_view snapshot on the same resumed navigation entry. Memory navigation, source content/assistant context changes, persistent favorites/collections and lifecycle actions are not performed. Returns actual requested/rendered readback; a pending view is acknowledged, not claimed rendered. Never replay an old snapshot.",
            schema(mapOf("expectedSnapshot" to token, "query" to mapOf("type" to "string", "maxLength" to 500),
                "filter" to mapOf("type" to "string", "enum" to NotebookFilter.entries.filter { it != NotebookFilter.MEMORY }.map { it.name }),
                "sort" to mapOf("type" to "string", "enum" to NotebookSort.entries.map { it.name }),
                "collectionId" to mapOf("type" to listOf("string", "null"), "maxLength" to 240),
                "selectedIds" to mapOf("type" to "array", "maxItems" to 100, "uniqueItems" to true, "items" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 240))), listOf("expectedSnapshot")))
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        if (toolCall.functionName !in NAMES) return reject(toolCall, ToolRejectionKind.TOOL_UNKNOWN)
        val scope = currentCoroutineContext()[RunToolScope]
        if (scope?.targetIsLocal != true) return reject(toolCall, ToolRejectionKind.PERMISSION_REQUIRED)
        val args = toolCall.arguments
        val expected = args["expectedSnapshot"] as? String
        if (("expectedSnapshot" in args && (expected == null || expected.length !in 1..100)) ||
            (toolCall.functionName == SET && expected == null)) return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
        val offset = if ("offset" !in args) 0 else (args["offset"] as? Number)?.let {
            if (it.toDouble() != it.toInt().toDouble() || it.toInt() !in 0..100000) null else it.toInt()
        } ?: return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
        val change = if (toolCall.functionName == SET) parseChange(args) ?: return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS) else null
        if (toolCall.functionName == READ && args.keys.any { it !in setOf("expectedSnapshot", "offset") }) return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
        return withContext(Dispatchers.Main.immediate) {
            val frame = if (change == null) session.read(expected) else session.set(expected!!, change)
            if (frame == null) return@withContext reject(toolCall)
            // SET has already consumed strict snapshot admission and changed the
            // requested view. Its own Room/debounce rendering may advance while
            // encoding; that is not a competing edit or a new content authority.
            val readback = if (change != null) session.readbackLease(frame.view.ownerRevision)
                ?: return@withContext reject(toolCall) else null
            val output = encode(frame, offset).toString()
            val permitted = if (change == null) session.read(frame.snapshot) != null
                else session.canReadback(readback)
            if (!permitted) return@withContext reject(toolCall)
            ToolExecutionResult.Success(toolCall.id, toolCall.functionName, output, ActionTruth.ACKNOWLEDGED)
        }
    }

    private fun parseChange(args: Map<String, Any?>): NotebookLibraryChange? {
        val keys = args.keys - "expectedSnapshot"
        if (keys.size != 1) return null
        return when (keys.single()) {
            "query" -> (args["query"] as? String)?.takeIf { it.length <= 500 }?.let { NotebookLibraryChange.Query(it) }
            "filter" -> NotebookFilter.entries.firstOrNull { it.name == args["filter"] && it != NotebookFilter.MEMORY }?.let { NotebookLibraryChange.Filter(it) }
            "sort" -> NotebookSort.entries.firstOrNull { it.name == args["sort"] }?.let { NotebookLibraryChange.Sort(it) }
            "collectionId" -> if (args["collectionId"] == null) NotebookLibraryChange.Collection(null) else
                (args["collectionId"] as? String)?.takeIf { it.length in 1..240 }?.let { NotebookLibraryChange.Collection(it) }
            "selectedIds" -> {
                val ids = args["selectedIds"] as? List<*> ?: return null
                if (ids.size > 100 || ids.any { it !is String || it.length !in 1..240 } || ids.toSet().size != ids.size) return null
                NotebookLibraryChange.Selection(ids.filterIsInstance<String>().toSet())
            }
            else -> null
        }
    }

    private fun encode(frame: NotebookLibraryFrame, offset: Int): JSONObject {
        val v = frame.view; val state = v.rendered
        val visible = state.items.filterNot { it.objectRef.id.value.startsWith("learning:") || state.filter == NotebookFilter.MEMORY }
        val rows = visible.drop(offset).take(100).map { item ->
            val o = item.objectRef
            JSONObject().put("id", o.id.value).put("type", o.type.name).put("lifecycle", o.lifecycle.name)
                .put("updatedAt", o.updatedAt).put("favorite", o.id.value in state.favorites).put("selected", o.id.value in v.selection)
        }
        return JSONObject().put("schemaVersion", 1).put("snapshot", frame.snapshot).put("settled", v.settled).put("mutating", v.mutating)
            .put("requested", settings(v.query, v.filter, v.sort, v.collectionId))
            .put("rendered", settings(state.resolvedQuery, state.filter, state.sort, state.selectedCollectionId))
            .put("selectedIds", JSONArray(v.selection.sorted())).put("rows", JSONArray(rows)).put("offset", offset)
            .put("totalRows", visible.size).put("nextOffset", if (offset + rows.size < visible.size) offset + rows.size else JSONObject.NULL)
            .put("collectionIds", JSONArray(state.collections.take(100).map { it.id })).put("totalCollections", state.collections.size)
            .put("collectionsTruncated", state.collections.size > 100).put("contentRead", false)
    }
    private fun settings(query: String, filter: NotebookFilter, sort: NotebookSort, collection: String?) =
        JSONObject().put("query", query).put("filter", filter.name).put("sort", sort.name).put("collectionId", collection ?: JSONObject.NULL)
    private fun reject(call: ToolCall, kind: ToolRejectionKind = ToolRejectionKind.RESOURCE_UNAVAILABLE) =
        ToolExecutionResult.Rejected(call.id, call.functionName, "Library is unavailable, settling, changed, or not permitted. Read the current local Library view again. No persistent action was performed.", kind)

    companion object {
        const val READ = "halo_library_view"
        const val SET = "halo_library_set_view"
        val NAMES = setOf(READ, SET)
        fun descriptor(definition: ToolDefinition): ToolDescriptor? {
            if (definition.name !in NAMES) return null
            return ToolDescriptor(ToolId(definition.name), definition, ToolCategory.INFORMATION,
                keywords = setOf("library", "workspace", "search", "filter", "selection", "sort"),
                requirements = setOf(ToolRequirement.Capability(operability(definition.name).capabilityId)),
                security = if (definition.name == READ) ToolSecurityPolicy.READ_ONLY else ToolSecurityPolicy(
                    risk = ToolRiskClass.REVERSIBLE, confirmation = ConfirmationPolicy.USER_POLICY, replaySafety = ReplaySafety.NEVER_REPLAY),
                execution = ToolExecutionPolicy(maxOutputChars = 128000),
                presentation = ToolPresentation("Library view", sensitiveArguments = setOf("query")),
                operability = operability(definition.name))
        }
        private val token = mapOf("type" to "string", "minLength" to 1, "maxLength" to 100)
        private fun schema(properties: Map<String, Any>, required: List<String> = emptyList()) =
            mapOf("type" to "object", "properties" to properties, "required" to required, "additionalProperties" to false)
        fun operability(name: String) = ToolOperability(version = 1, capabilityId = CapabilityId("host.assistant.library_view"),
            owner = "NotebookViewModel/resumed Library entry", operation = if (name == READ) ToolOperation.QUERY else ToolOperation.SELECT,
            resultEncoding = ToolResultEncoding.JSON, sourceVersionArguments = setOf("expectedSnapshot"), resultIdFields = setOf("snapshot"),
            resultSchema = mapOf("type" to "object", "properties" to mapOf("schemaVersion" to mapOf("type" to "integer"),
                "snapshot" to token, "settled" to mapOf("type" to "boolean"), "requested" to mapOf("type" to "object"),
                "rendered" to mapOf("type" to "object"), "rows" to mapOf("type" to "array"), "contentRead" to mapOf("type" to "boolean"))),
            limitations = setOf("Foreground grown-up local owner only. No implicit source content read/upload or assistant context grant.",
                "Snapshot is a view admission token, not canonical source version; pending debounced results are explicitly unsettled.",
                "View operations do not implement persistent organization, lifecycle, batch, gestures or actual spoken/provider acceptance."))
    }
}
