package com.halo.app.di.tools.host

import com.halo.runtime.cognition.tools.*

/** Integration metadata only; never grants permission or bypasses the existing authorizer. */
object AppPreferenceOperability {
    fun forName(name: String): ToolOperability? {
        val profile = name in setOf("halo_get_mode_profile", "halo_edit_mode_profile")
        if (!profile && name !in setOf("halo_get_preferences", "halo_set_preferences")) return null
        val query = name in setOf("halo_get_preferences", "halo_get_mode_profile")
        return ToolOperability(capabilityId = CapabilityId(if (profile) "host.assistant.profiles" else "host.assistant.preferences"),
            owner = if (profile) "PrivacyModeStore/ManualDependencyGraph.saveModeProfile" else "AppearancePreferences/AssistantPreferences/PalettePreferences/VisualPreferences/AppLanguagePreferences",
            operation = if (query) ToolOperation.QUERY else ToolOperation.EDIT,
            resultEncoding = if (profile) ToolResultEncoding.JSON else ToolResultEncoding.TEXT,
            resultSchema = if (!profile) mapOf("type" to "string", "maxLength" to 24000) else mapOf(
                "type" to "object", "properties" to mapOf("mode" to mapOf("type" to "string"),
                    "revision" to mapOf("type" to "integer"), "snapshot" to mapOf("type" to "string"),
                    "activeMode" to mapOf("type" to "string"), "sharingAcknowledged" to mapOf("type" to "boolean"),
                    "activationPerformed" to mapOf("type" to "boolean"), "paidConsentChanged" to mapOf("type" to "boolean"),
                    "providerId" to mapOf("type" to listOf("string", "null"), "maxLength" to 200),
                    "modelId" to mapOf("type" to listOf("string", "null"), "maxLength" to 200),
                    "routing" to mapOf("type" to listOf("string", "null"), "maxLength" to 200),
                    "sttProvider" to mapOf("type" to listOf("string", "null"), "maxLength" to 200),
                    "ttsProvider" to mapOf("type" to listOf("string", "null"), "maxLength" to 200),
                    "booleans" to mapOf("type" to "object", "properties" to
                        (com.halo.app.ai.profileBooleanKeys - "active_ai_selection_migrated_v1").associateWith { mapOf("type" to "boolean") },
                        "additionalProperties" to false),
                    "onlineFeatures" to mapOf("type" to "array", "maxItems" to com.halo.app.ai.OnlineFeature.entries.size,
                        "items" to mapOf("type" to "string", "enum" to com.halo.app.ai.OnlineFeature.entries.map { it.name })),
                    "localFallback" to mapOf("type" to "boolean"), "changed" to mapOf("type" to "boolean"),
                    "selectionTruncated" to mapOf("type" to "boolean")),
                "required" to listOf("mode", "revision", "snapshot")),
            sourceVersionArguments = if (name == "halo_edit_mode_profile") setOf("revision", "snapshot") else emptySet(),
            resultIdFields = if (profile) setOf("mode") else emptySet(),
            limitations = setOf("UI owners persist requested app settings only; no OS grants, provider call, mode activation or paid consent.",
                "Expanded sharing requires existing human disclosure review. Uncertain writes are never replayed."))
    }
}
