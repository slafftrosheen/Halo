package com.halo.app.di.tools.host

import com.halo.app.ai.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Bounded non-secret profile projection. The UI save callback retains parental/owner preparation gates. */
class ModeProfileTools(
    private val modes: PrivacyModeStore,
    /** Must compare expectedRevision inside the existing PrivacyModeStore.edit change transaction. */
    private val saveProfile: (PrivacyMode, ModeProfile, Long) -> Unit,
) : ToolExecutor {
    companion object {
        private val strings = linkedMapOf("providerId" to "active_ai_provider", "modelId" to "active_ai_model",
            "routing" to "ai_routing_policy", "sttProvider" to "speech_stt_provider", "ttsProvider" to "speech_tts_provider")
        private val contextKeys = profileBooleanKeys - setOf("active_ai_selection_migrated_v1")
        private val modeSchema = mapOf("type" to "string", "enum" to PrivacyMode.entries.map { it.name })
        private val revisionSchema = mapOf("type" to "integer", "minimum" to 0)
        private val snapshotSchema = mapOf("type" to "string", "pattern" to "^[a-f0-9]{64}$")
        private val editFields = mapOf<String, Any>("mode" to modeSchema, "revision" to revisionSchema, "snapshot" to snapshotSchema,
            "booleans" to mapOf("type" to "object", "properties" to contextKeys.associateWith { mapOf("type" to "boolean") }, "additionalProperties" to false),
            "onlineFeatures" to mapOf("type" to "array", "maxItems" to OnlineFeature.entries.size, "uniqueItems" to true,
                "items" to mapOf("type" to "string", "enum" to OnlineFeature.entries.map { it.name })),
            "localFallback" to mapOf("type" to "boolean")) + strings.keys.associateWith { mapOf("type" to "string", "maxLength" to 200) }
    }
    private fun definition(name: String, description: String, fields: Map<String, Any>, required: List<String>) =
        ToolDefinition(name, name, description, mapOf("type" to "object", "properties" to fields, "required" to required, "additionalProperties" to false))
    override fun definitions() = listOf(
        definition("halo_get_mode_profile", "Read one exact Private or Power processing profile, global revision and SHA256 snapshot before edits. No credentials/endpoints, activation, provider calls, sharing acknowledgement or paid consent. CLOUD identifies the saved Power profile.",
            mapOf("mode" to modeSchema), listOf("mode")),
        definition("halo_edit_mode_profile", "After explicit approval edit only supplied non-secret processing fields of an exact observed profile revision and snapshot through the existing UI save owner. Never activates a mode or acknowledges sharing/paid/OS permissions. Expanded remote sharing/provider choices require existing human disclosure review and are rejected here. Empty selection text clears it. Stale/uncertain writes stop; reread and obtain a fresh decision instead of replaying.",
            editFields, listOf("mode", "revision", "snapshot")),
    )
    private fun profileJson(profile: ModeProfile): JSONObject = JSONObject().apply {
        strings.forEach { (field, key) -> put(field, profile.strings[key]?.take(200) ?: JSONObject.NULL) }
        put("selectionTruncated", strings.values.any { (profile.strings[it]?.length ?: 0) > 200 })
        put("booleans", JSONObject().apply { contextKeys.sorted().forEach { put(it, profile.booleans[it] == true) } })
        put("onlineFeatures", JSONArray(profile.onlineFeatures.map { it.name }.sorted()))
        put("localFallback", profile.localFallback)
    }
    private fun digest(profile: ModeProfile): String {
        val canonical = profile.strings.toSortedMap().entries.joinToString("\n") { (key, value) -> "$key=${value?.length ?: -1}:$value" } +
            "\n" + profile.booleans.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" } +
            "\n" + profile.onlineFeatures.map { it.name }.sorted().joinToString(",") + "\n" + profile.localFallback
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    private fun read(mode: PrivacyMode): Triple<ModeProfile, Long, String> {
        repeat(3) {
            val before = modes.revision
            val profile = modes.profile(mode)
            if (before == modes.revision) return Triple(profile, before, digest(profile))
        }
        error("Profile is changing. Read it again.")
    }
    private fun result(mode: PrivacyMode, profile: ModeProfile, revision: Long, snapshot: String) = profileJson(profile)
        .put("mode", mode.name).put("revision", revision).put("snapshot", snapshot)
        .put("activeMode", modes.mode.value.name).put("sharingAcknowledged", modes.sharingAcknowledged)
        .put("activationPerformed", false).put("paidConsentChanged", false)
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(message: String, kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) =
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, message, kind)
        val args = toolCall.arguments
        val mode = PrivacyMode.entries.firstOrNull { it.name == args["mode"] } ?: return reject("Specify PRIVATE or CLOUD")
        if (toolCall.functionName !in setOf("halo_get_mode_profile", "halo_edit_mode_profile")) return reject("Unknown profile operation", ToolRejectionKind.TOOL_UNKNOWN)
        val reading = toolCall.functionName == "halo_get_mode_profile"
        var saveStarted = false
        if (reading && args.keys != setOf("mode") || !reading && (args.keys.any { it !in editFields } || !args.keys.containsAll(setOf("mode", "revision", "snapshot"))))
            return reject("Use only the closed profile argument fields")
        try {
            val (old, revision, snapshot) = read(mode)
            if (reading) return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, result(mode, old, revision, snapshot).toString(), ActionTruth.ACKNOWLEDGED)
            val observed = args["revision"] as? Number ?: return reject("Expected integer revision")
            if (observed.toDouble() != observed.toLong().toDouble() || observed.toLong() != revision || args["snapshot"] != snapshot)
                return reject("Profile changed. Read it again and request a fresh edit.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
            if (args.keys == setOf("mode", "revision", "snapshot")) return reject("Supply an explicit profile change")
            var nextStrings = old.strings
            strings.forEach { (field, key) -> if (field in args) {
                val value = args[field] as? String ?: return reject("Expected text for $field")
                if (value.length > 200 || value.any(Char::isISOControl)) return reject("Invalid profile text")
                nextStrings = nextStrings + (key to value.takeIf { it.isNotBlank() })
            } }
            val routing = nextStrings["ai_routing_policy"]
            if (routing !in setOf("LOCAL_ONLY", "EXPLICIT", "AUTOMATIC", "PREFERRED_WITH_FALLBACK")) return reject("Invalid routing policy")
            if (mode == PrivacyMode.PRIVATE && (routing !in setOf("LOCAL_ONLY", "EXPLICIT") || nextStrings["active_ai_provider"] !in setOf(null, "provider.litert.local")))
                return reject("Private profiles require on-device processing")
            if ("sttProvider" in args && nextStrings["speech_stt_provider"] !in
                (setOf(null, "AUTOMATIC") + com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog.recognitionModels.map { it.providerId }))
                return reject("Choose an existing speech-input provider")
            val localVoices = setOf(null, "AUTOMATIC", "provider.android.tts", com.halo.app.audio.KokoroSpeechProvider.ID,
                com.halo.app.audio.SupertonicSpeechProvider.ID)
            if ("ttsProvider" in args && nextStrings["speech_tts_provider"] !in
                (if (mode == PrivacyMode.PRIVATE) localVoices else localVoices + com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider.ID))
                return reject("Choose an existing permitted speech-output provider")
            var booleans = old.booleans
            if ("booleans" in args) {
                val values = args["booleans"] as? Map<*, *> ?: return reject("Expected closed context options")
                if (values.keys.any { it !in contextKeys } || values.values.any { it !is Boolean }) return reject("Invalid context option")
                booleans = booleans + values.entries.associate { (it.key as String) to (it.value as Boolean) }
            }
            var features = old.onlineFeatures
            if ("onlineFeatures" in args) {
                val values = args["onlineFeatures"] as? List<*> ?: return reject("Expected online feature list")
                if (values.size != values.toSet().size || values.any { value -> OnlineFeature.entries.none { it.name == value } }) return reject("Invalid online feature")
                features = values.map { value -> OnlineFeature.valueOf(value as String) }.toSet()
            }
            if (mode == PrivacyMode.PRIVATE && features.isNotEmpty()) return reject("Private profiles cannot enable online features")
            if ("localFallback" in args && args["localFallback"] !is Boolean) return reject("localFallback must be boolean")
            val fallback = if ("routing" in args) mode == PrivacyMode.CLOUD && routing == "PREFERRED_WITH_FALLBACK" else
                args["localFallback"] as? Boolean ?: old.localFallback
            if (mode == PrivacyMode.PRIVATE && fallback || "localFallback" in args && args["localFallback"] != fallback)
                return reject("Fallback must match the selected routing policy")
            val next = old.copy(strings = nextStrings, booleans = booleans, onlineFeatures = features, localFallback = fallback)
            if (mode == PrivacyMode.CLOUD && ((next.enabledRemoteCategories() - old.enabledRemoteCategories()).isNotEmpty() ||
                    (next.onlineFeatures - old.onlineFeatures).isNotEmpty() || next.providerId != old.providerId ||
                    next.strings["speech_tts_provider"] == com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider.ID &&
                        next.strings["speech_tts_provider"] != old.strings["speech_tts_provider"]))
                return reject("Expanded sharing or a different remote provider needs the existing Mode profiles disclosure review. No profile was changed.", ToolRejectionKind.PERMISSION_REQUIRED)
            if (next == old) return ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                result(mode, old, revision, snapshot).put("changed", false).toString(), ActionTruth.ACKNOWLEDGED)
            saveStarted = true
            withContext(Dispatchers.IO) { saveProfile(mode, next, revision) }
            val (saved, savedRevision, savedHash) = read(mode)
            check(saved == next) { "Profile readback differs" }
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                result(mode, saved, savedRevision, savedHash).put("changed", true).toString(), ActionTruth.CONFIRMED)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            val reason = "Profile could not be saved or changed during review. Read current state before a fresh request; no automatic retry."
            return if (saveStarted) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName, reason,
                completionUnknown = true, failureKind = ToolFailureKind.UNKNOWN)
            else reject(reason, ToolRejectionKind.RESOURCE_UNAVAILABLE)
        }
    }
}
