package com.halo.app.di.tools

import com.halo.app.ai.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.CancellationException

/** Requests the existing installer; never selects/qualifies models or owns downloads itself. */
class LocalModelSetupToolExecutor(private val owner: LocalRolePackControl,
    private val reviewLanguage: () -> String = { "en" },
) : ToolExecutor {
    private val commandNames = mapOf("halo_local_setup_start" to LocalRolePackCommand.START_OR_RESUME,
        "halo_local_setup_pause" to LocalRolePackCommand.PAUSE, "halo_local_setup_rollback" to LocalRolePackCommand.ROLLBACK,
        "halo_local_setup_keep_files" to LocalRolePackCommand.KEEP_FILES)
    override fun definitions() = listOf(
        ToolDefinition("halo_local_setup_status", "halo_local_setup_status", "Read compact starter/optional conversation-extension acquisition status and exact transaction IDs. With view=SOURCE_REVIEW and scope=STARTER or CONVERSATION_EXTENSION (default STARTER), read the complete bounded approval summary instead of action status; pass its exact text and fingerprint to START for first acknowledgement. With manifestArtifactIndex, read ONE bounded artifact detail page instead of action status. Download bytes exclude temporary space; execution checks again. Pack inspection cannot attest role/device/backend/language qualification. Files installed does not mean selected, recommended or audible. No download, inference or provider call.",
            mapOf("type" to "object", "properties" to mapOf("manifestArtifactIndex" to mapOf(
                "type" to "integer", "minimum" to 0,
                "maximum" to (PrivateOfflinePack.artifacts + PrivateOfflinePack.optionalArtifacts).lastIndex),
                "view" to mapOf("type" to "string", "enum" to listOf("SOURCE_REVIEW")),
                "scope" to mapOf("type" to "string", "enum" to listOf("STARTER", "CONVERSATION_EXTENSION"),
                    "description" to "Only with view=SOURCE_REVIEW; binds human approval to this exact download scope. Defaults to STARTER.")),
                "additionalProperties" to false)),
    ) + commandNames.map { (name, command) -> ToolDefinition(name, name, when (command) {
        LocalRolePackCommand.START_OR_RESUME -> "After explicit approval request/resume/retry downloads for the chosen scope. STARTER acquires intent, recognition, VAD and speech output; CONVERSATION_EXTENSION acquires only the pinned optional SmolLM2 360M candidate. Can require substantial network/storage. Reuses existing installer; never selects candidates or qualifies languages/devices. Review source/license disclosure first; if not already acknowledged supply both reviewedManifestFingerprint and exact sourceReviewSummary from the SOURCE_REVIEW status view for durable user approval (unknown licensing remains unknown). Status first; supply that scope's exact transactionId (NONE for fresh setup). Never replay uncertain requests."
        LocalRolePackCommand.PAUSE -> "Pause only the exact observed setup scope and its owner-tagged downloads. Keeps resumable files; never cancels unrelated acquisitions. No automatic resume."
        LocalRolePackCommand.ROLLBACK -> "Request removal of only artifacts proven owned by the exact setup transaction, with existing required approval. Keeps unrelated, pre-existing, changed, selected or in-use models. Legacy/unknown ownership needs explicit keep-files recovery. Dispatch is not completed removal."
        LocalRolePackCommand.KEEP_FILES -> "Explicitly keep all downloaded files/selections and clear only the exact setup receipt. This ends its automatic rollback option; no files are removed, no download resumes and no role is selected."
    }, mapOf("type" to "object", "properties" to mapOf("transactionId" to mapOf("type" to "string",
        "pattern" to "^(NONE|[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})$"),
        "reviewedManifestFingerprint" to mapOf("type" to "string", "pattern" to "^[a-f0-9]{64}$",
            "description" to "START only: exact manifest fingerprint explicitly reviewed by the user, acknowledging declared and missing license information. Does not certify licensing or device qualification."),
        "sourceReviewSummary" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 2000,
            "description" to "START only: exact complete sourceReviewSummary from status view SOURCE_REVIEW, shown by ordinary durable approval. Required together with reviewedManifestFingerprint for first review; never paraphrase or truncate."),
        "scope" to mapOf("type" to "string", "enum" to listOf("STARTER", "CONVERSATION_EXTENSION"),
            "description" to "Default STARTER. CONVERSATION_EXTENSION manages only the optional exact pinned SmolLM2 360M download through the same owner. Use that scope's observed transactionId; no role selection or qualification.")),
        "required" to listOf("transactionId"), "additionalProperties" to false)) }
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        if (toolCall.functionName == "halo_local_setup_status") {
            if (toolCall.arguments.keys.any { it !in setOf("manifestArtifactIndex", "view", "scope") }) return rejected(toolCall)
            if ("view" in toolCall.arguments) {
                if (toolCall.arguments.keys.any { it !in setOf("view", "scope") } || toolCall.arguments["view"] != "SOURCE_REVIEW")
                    return rejected(toolCall)
                if ("scope" in toolCall.arguments && toolCall.arguments["scope"] !is String) return rejected(toolCall)
                val scope = toolCall.arguments["scope"] as? String ?: "STARTER"
                if (scope !in setOf("STARTER", "CONVERSATION_EXTENSION")) return rejected(toolCall)
                val summary = PrivateOfflinePack.sourceReviewSummary(scope, reviewLanguage())
                    ?: return rejected(toolCall, ToolRejectionKind.RESOURCE_UNAVAILABLE)
                val output = JSONObject().put("view", "SOURCE_REVIEW").put("actionStatusIncluded", false).put("scope", scope)
                    .put("transactionId", owner.state.value.transactionId ?: "NONE")
                    .put("manifestFingerprint", PrivateOfflinePack.fingerprint)
                    .put("sourceReviewSummary", summary)
                    .put("sourceReviewAcknowledged", owner.sourceReviewAcknowledged)
                    .put("licenseAuditComplete", false).toString()
                if (output.length > 3_800) return rejected(toolCall, ToolRejectionKind.RESOURCE_UNAVAILABLE)
                return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, output)
            }
            if ("scope" in toolCall.arguments) return rejected(toolCall)
            if ("manifestArtifactIndex" in toolCall.arguments) {
                val index = toolCall.arguments["manifestArtifactIndex"] as? Number ?: return rejected(toolCall)
                val artifacts = PrivateOfflinePack.artifacts + PrivateOfflinePack.optionalArtifacts
                val offset = index.toInt()
                if (index.toDouble() != offset.toDouble() || offset !in artifacts.indices) return rejected(toolCall)
                val artifact = artifacts[offset]
                return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, JSONObject()
                    .put("transactionId", owner.state.value.transactionId ?: "NONE")
                    .put("view", "MANIFEST_ARTIFACT").put("actionStatusIncluded", false)
                    .put("manifestFingerprint", PrivateOfflinePack.fingerprint)
                    .put("artifactIndex", offset).put("artifactCount", artifacts.size)
                    .put("artifact", JSONObject().put("id", artifact.id).put("role", artifact.role.name)
                        .put("source", artifact.source).put("includedInStarterDownload", artifact in PrivateOfflinePack.artifacts)
                        .put("revision", artifact.revision ?: JSONObject.NULL)
                        .put("declaredLicense", artifact.declaredLicense ?: JSONObject.NULL)
                        .put("runtime", artifact.runtime).put("exactFilePins", artifact.hasExactFilePins)
                        .put("declaredLanguages", JSONArray(artifact.declaredLanguages.sorted()))
                        .put("qualificationStatus", "NOT_ATTESTED_BY_PACK_INSPECTION")
                        .put("files", JSONArray(artifact.files.map { file -> JSONObject()
                            .put("name", file.name).put("bytes", file.bytes).put("sha256", file.sha256 ?: JSONObject.NULL) })))
                    .toString())
            }
            val value = owner.state.value
            val preview = try { owner.downloadPreview() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            val inventory = try { owner.offlineInventory() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            val extension = try { owner.conversationExtensionState() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, JSONObject().put("available", owner.available)
                .put("transactionId", value.transactionId ?: "NONE").put("phase", value.phase.name).put("stage", value.stage.name)
                .put("busy", value.busy).put("canResume", value.canResume).put("canRollback", value.canRollback)
                .put("hasSetupRecord", value.hasSetupRecord).put("recoveryRequired", value.recoveryRequired)
                .put("canKeepFiles", value.canKeepFiles)
                .put("recoveryHint", if (value.message == LOCAL_ROLE_PACK_REPAIR_REQUIRED)
                    "Existing speech files need explicit repair/removal in Voice settings; they were retained. Retry setup after repair."
                    else JSONObject.NULL)
                .put("conversationExtension", extension?.let { chat -> JSONObject()
                    .put("scope", "CONVERSATION_EXTENSION").put("transactionId", chat.transactionId)
                    .put("available", chat.available).put("busy", chat.busy)
                    .put("recoveryRequired", chat.recoveryRequired).put("phase", chat.phase)
                    .put("installedAndIntact", chat.installedAndIntact)
                    .put("remainingDownloadBytes", chat.remainingDownloadBytes)
                    .put("requiredStorageBytes", chat.requiredStorageBytes)
                    .put("availableStorageBytes", chat.availableStorageBytes)
                    .put("hasEnoughStorage", chat.hasEnoughStorage)
                    .put("message", chat.message ?: JSONObject.NULL)
                    .put("qualificationGranted", false).put("roleSelectionChanged", false)
                    .put("rollbackOnlyOwnedFiles", true)
                } ?: JSONObject.NULL)
                .put("downloadedBytes", value.downloadedBytes).put("totalBytes", value.totalBytes)
                .put("downloadPreview", preview?.let { plan -> JSONObject()
                    .put("estimatedRemainingDownloadBytes", plan.downloadBytes)
                    .put("estimatedRequiredStorageBytes", plan.requiredStorageBytes)
                    .put("enoughStorageAtObservation", plan.hasEnoughStorage)
                    .put("chatModelIncluded", false)
                    .put("downloads", JSONArray(plan.downloads.map { item -> JSONObject()
                        .put("stage", item.stage.name).put("remainingBytes", item.remainingBytes) }))
                    .put("storageVolumes", JSONArray(plan.storage.map { item -> JSONObject()
                        .put("requiredBytes", item.requiredBytes).put("availableBytes", item.availableBytes) }))
                } ?: JSONObject.NULL)
                .put("offlinePack", JSONObject().put("version", PrivateOfflinePack.VERSION)
                    .put("manifestFingerprint", PrivateOfflinePack.fingerprint)
                    .put("qualificationStatus", inventory?.qualificationStatus ?: "NOT_ATTESTED_BY_PACK_INSPECTION")
                    .put("historicalFastIntentEvidence", JSONObject().put("issue", 36)
                        .put("scope", "Historical Qwen CPU candidate; not a live device/backend verdict")
                        .put("result", "FAILED_2S_REQUIREMENT").put("observedMs", 9580).put("requiredMs", 2000))
                    .put("licenseAuditComplete", false)
                    .put("sourceReviewAcknowledged", owner.sourceReviewAcknowledged)
                    .put("missingStarterRoles", JSONArray(PrivateOfflinePack.missingRoles.map { it.name }))
                    .put("manifestArtifactCount", PrivateOfflinePack.artifacts.size + PrivateOfflinePack.optionalArtifacts.size)
                    .put("manifestPageArgument", "manifestArtifactIndex")
                    .put("inventory", inventory?.let { checked -> JSONObject()
                        .put("integrityCheckedRoles", JSONArray(checked.verifiedRoles.map { it.name }))
                        .put("missingFileRoles", JSONArray(checked.missingFileRoles.map { it.name }))
                        .put("compatibleSpeechPackFingerprints", JSONArray(checked.compatibleSpeechPackFingerprints.take(4)))
                        .put("compatibleSpeechPackCount", checked.compatibleSpeechPackFingerprints.size)
                        .put("selectedSupertonicPackPresent", checked.selectedSupertonicPackPresent)
                        .put("selectedSupertonicVoiceAudibleConfirmed", checked.selectedSupertonicVoiceAudibleConfirmed)
                    } ?: JSONObject.NULL))
                .put("roleSelectionChanged", false).put("qualificationGranted", false).toString())
        }
        val command = commandNames[toolCall.functionName] ?: return rejected(toolCall, ToolRejectionKind.TOOL_UNKNOWN)
        if (!toolCall.arguments.keys.contains("transactionId") ||
            toolCall.arguments.keys.any { it !in setOf("transactionId", "scope", "reviewedManifestFingerprint", "sourceReviewSummary") }) return rejected(toolCall)
        if ("scope" in toolCall.arguments && toolCall.arguments["scope"] !is String) return rejected(toolCall)
        val requestedScope = toolCall.arguments["scope"] as? String ?: "STARTER"
        if (requestedScope !in setOf("STARTER", "CONVERSATION_EXTENSION")) return rejected(toolCall)
        val token = toolCall.arguments["transactionId"] as? String ?: return rejected(toolCall)
        if (token != "NONE" && runCatching { java.util.UUID.fromString(token).toString() == token }.getOrDefault(false) != true) return rejected(toolCall)
        if (requestedScope == "STARTER" && command == LocalRolePackCommand.START_OR_RESUME && !owner.available)
            return rejected(toolCall, ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        if ("reviewedManifestFingerprint" in toolCall.arguments || "sourceReviewSummary" in toolCall.arguments) {
            if (command != LocalRolePackCommand.START_OR_RESUME) return rejected(toolCall)
            val reviewed = toolCall.arguments["reviewedManifestFingerprint"] as? String ?: return rejected(toolCall)
            val summary = toolCall.arguments["sourceReviewSummary"] as? String ?: return rejected(toolCall)
            val canonical = PrivateOfflinePack.sourceReviewSummary(requestedScope, reviewLanguage()) ?:  return rejected(toolCall, ToolRejectionKind.RESOURCE_UNAVAILABLE)
            if (summary != canonical || reviewed != PrivateOfflinePack.fingerprint) return rejected(toolCall)
            if (!owner.acknowledgeSourceReview(reviewed, true)) return rejected(toolCall)
        }
        if (command == LocalRolePackCommand.START_OR_RESUME && !owner.sourceReviewAcknowledged)
            return rejected(toolCall, ToolRejectionKind.RESOURCE_UNAVAILABLE)
        val accepted = if (requestedScope == "CONVERSATION_EXTENSION") owner.requestConversationExtension(command, token)
            else owner.request(command, token)
        if (!accepted) return rejected(toolCall, ToolRejectionKind.RESOURCE_UNAVAILABLE)
        return ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
            JSONObject().put("transactionId", token).put("scope", requestedScope).put("state", "DISPATCHED").put("action", command.name).toString(), ActionTruth.DISPATCHED)
    }
    private fun rejected(call: ToolCall, kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(
        call.id, call.functionName, "Setup request unavailable or changed. Read setup status and review the current transaction before continuing.", kind)
}
