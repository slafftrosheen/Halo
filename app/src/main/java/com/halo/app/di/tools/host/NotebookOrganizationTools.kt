package com.halo.app.di.tools.host

import com.halo.app.data.notebook.NotebookOrganizationChange
import com.halo.app.ui.screens.notebook.NotebookLibrarySession
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Typed organization operations through the existing VM and Room organization owner. */
class NotebookOrganizationTools(private val session: NotebookLibrarySession) : ToolExecutor {
    override fun definitions() = listOf(
        ToolDefinition(READ, READ, "Explicitly read local favorite/membership metadata for an object visible in the current Library and/or an existing collection. Requires its fresh settled halo_library_view snapshot. Returns a one-use organization snapshot for guarded changes. No title, document content, source context attachment, remote disclosure or navigation. Omit targets when preparing creation of a collection.",
            schema(mapOf("expectedSnapshot" to token, "objectId" to id, "collectionId" to id), listOf("expectedSnapshot"))),
        ToolDefinition(WRITE, WRITE, "After explicit approval, apply exactly one persisted Library organization operation: set_favorite(objectId,value), set_membership(objectId,collectionId,value), create_collection(name), delete_collection(collectionId). Explicit target IDs must match the organization read. Requires the exact view and one-use organization snapshots returned by local read tools. Source row, favorite, collection list and relevant memberships are compared atomically within the existing Room transaction; changed source/selection/entry is rejected. Delete collection removes its organization record/memberships, never its source objects. No mixed batch/archive/trash/restore/purge. Never retry/replay without a fresh read and approval.",
            schema(mapOf("expectedSnapshot" to token, "expectedOrganization" to token,
                "operation" to mapOf("type" to "string", "enum" to listOf("set_favorite", "set_membership", "create_collection", "delete_collection")),
                "objectId" to id, "collectionId" to id, "value" to mapOf("type" to "boolean"), "name" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 60)),
                listOf("expectedSnapshot", "expectedOrganization", "operation")))
    )
    override suspend fun execute(toolCall: ToolCall) = execute(toolCall, ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        if (toolCall.functionName !in NAMES) return reject(toolCall, ToolRejectionKind.TOOL_UNKNOWN)
        if (currentCoroutineContext()[RunToolScope]?.targetIsLocal != true) return reject(toolCall, ToolRejectionKind.PERMISSION_REQUIRED)
        if (toolCall.functionName == WRITE && context.interactionId.isNullOrBlank()) return reject(toolCall, ToolRejectionKind.PERMISSION_REQUIRED)
        val args = toolCall.arguments
        val expected = (args["expectedSnapshot"] as? String)?.takeIf { it.length in 1..100 }
            ?: return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
        if (toolCall.functionName == READ) {
            if (args.keys.any { it !in setOf("expectedSnapshot", "objectId", "collectionId") }) return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
            for (key in listOf("objectId", "collectionId")) if (key in args && ((args[key] as? String)?.takeIf { it.length in 1..240 } == null))
                return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
            return withContext(Dispatchers.Main.immediate) {
                val result = session.organization(expected, args["objectId"] as? String, args["collectionId"] as? String)
                    ?: return@withContext reject(toolCall)
                val data = result.data
                val output = JSONObject().put("schemaVersion", 1).put("snapshot", expected).put("organizationSnapshot", result.token)
                    .put("objectId", data.objectId ?: JSONObject.NULL).put("collectionId", data.collectionId ?: JSONObject.NULL)
                    .put("favorite", data.favorite != null).put("member", data.members.any { it.objectId == data.objectId })
                    .put("collectionExists", data.collections.any { it.id == data.collectionId }).put("contentRead", false)
                ToolExecutionResult.Success(toolCall.id, READ, output.toString(), ActionTruth.ACKNOWLEDGED)
            }
        }
        val organization = (args["expectedOrganization"] as? String)?.takeIf { it.length in 1..100 }
            ?: return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
        val change = when (args["operation"]) {
            "set_favorite", "set_membership" -> {
                val targetKeys = if (args["operation"] == "set_favorite") setOf("objectId") else setOf("objectId", "collectionId")
                if (args.keys != setOf("expectedSnapshot", "expectedOrganization", "operation", "value") + targetKeys ||
                    targetKeys.any { (args[it] as? String)?.takeIf { id -> id.length in 1..240 } == null }) return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
                val value = args["value"] as? Boolean ?: return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
                if (args["operation"] == "set_favorite") NotebookOrganizationChange.Favorite(value) else NotebookOrganizationChange.Membership(value)
            }
            "create_collection" -> {
                if (args.keys != setOf("expectedSnapshot", "expectedOrganization", "operation", "name")) return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
                val name = (args["name"] as? String)?.trim()?.takeIf { it.length in 1..60 } ?: return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
                NotebookOrganizationChange.CreateCollection(name)
            }
            "delete_collection" -> {
                if (args.keys != setOf("expectedSnapshot", "expectedOrganization", "operation", "collectionId") ||
                    (args["collectionId"] as? String)?.takeIf { it.length in 1..240 } == null) return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
                NotebookOrganizationChange.DeleteCollection
            }
            else -> return reject(toolCall, ToolRejectionKind.INVALID_ARGUMENTS)
        }
        return withContext(Dispatchers.Main.immediate) {
            try {
            val readback = session.readbackLease() ?: return@withContext reject(toolCall)
            val result = try {
                session.organize(expected, organization, change, args["objectId"] as? String, args["collectionId"] as? String)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { return@withContext uncertain(toolCall) }
            if (result == null) return@withContext reject(toolCall)
            if (!session.canReadback(readback)) return@withContext uncertain(toolCall)
            val output = JSONObject().put("schemaVersion", 1).put("operation", result.operation)
                .put("objectId", result.objectId ?: JSONObject.NULL).put("collectionId", result.collectionId ?: JSONObject.NULL)
                .put("value", result.value ?: JSONObject.NULL).put("applied", true).put("readbackConfirmed", true)
            ToolExecutionResult.Success(toolCall.id, WRITE, output.toString(), ActionTruth.CONFIRMED)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { uncertain(toolCall) }
        }
    }
    private fun uncertain(call: ToolCall) = ToolExecutionResult.Failed(call.id, call.functionName,
        "Library organization outcome cannot be disclosed or confirmed in this context. Do not replay; return to an authorized Library and read its current state.",
        completionUnknown = true, code = "LIBRARY_ORGANIZATION_UNCERTAIN")
    private fun reject(call: ToolCall, kind: ToolRejectionKind = ToolRejectionKind.RESOURCE_UNAVAILABLE) =
        ToolExecutionResult.Rejected(call.id, call.functionName, "Library organization target is unavailable, stale, unsupported or not permitted. Refresh the local view and organization snapshots before another request.", kind)
    companion object {
        const val READ = "halo_library_organization"
        const val WRITE = "halo_library_organize"
        val NAMES = setOf(READ, WRITE)
        fun descriptor(definition: ToolDefinition): ToolDescriptor? {
            if (definition.name !in NAMES) return null
            return ToolDescriptor(ToolId(definition.name), definition, ToolCategory.INFORMATION,
                keywords = setOf("library", "collection", "favorite", "membership"),
                requirements = setOf(ToolRequirement.Capability(operability(definition.name).capabilityId)),
                security = if (definition.name == READ) ToolSecurityPolicy.READ_ONLY else ToolSecurityPolicy(
                    risk = ToolRiskClass.SENSITIVE, confirmation = ConfirmationPolicy.ALWAYS, replaySafety = ReplaySafety.NEVER_REPLAY),
                presentation = ToolPresentation("Library organization", actionVerb = "Change", sensitiveArguments = setOf("name")),
                operability = operability(definition.name))
        }
        private val token = mapOf("type" to "string", "minLength" to 1, "maxLength" to 100)
        private val id = mapOf("type" to "string", "minLength" to 1, "maxLength" to 240)
        private fun schema(properties: Map<String, Any>, required: List<String>) = mapOf("type" to "object", "properties" to properties,
            "required" to required, "additionalProperties" to false)
        fun operability(name: String) = ToolOperability(version = 1, capabilityId = CapabilityId("host.assistant.library_organization"),
            owner = "NotebookViewModel/NotebookOrganizationRepository/Room", operation = if (name == READ) ToolOperation.QUERY else ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON, sourceVersionArguments = if (name == READ) setOf("expectedSnapshot") else setOf("expectedSnapshot", "expectedOrganization"),
            resultIdFields = setOf("objectId", "collectionId"),
            resultSchema = mapOf("type" to "object", "properties" to mapOf("schemaVersion" to mapOf("type" to "integer"),
                "objectId" to mapOf("type" to listOf("string", "null")), "collectionId" to mapOf("type" to listOf("string", "null")),
                "organizationSnapshot" to token, "applied" to mapOf("type" to "boolean"), "readbackConfirmed" to mapOf("type" to "boolean"))),
            limitations = setOf("Foreground grown-up local Library only; writes require ALWAYS approval and NEVER_REPLAY registration.",
                "Only explicit favorite/membership/collection operations, not mixed batch lifecycle, document content, remote disclosure or actual voice acceptance.",
                "Favorite/membership changes reject trashed objects. Metadata comparison is exact inside the existing transaction; view lease is checked at admission."))
    }
}
