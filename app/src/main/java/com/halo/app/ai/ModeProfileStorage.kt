package com.halo.app.ai

import com.halo.sdk.provider.AiModelRole
import com.halo.sdk.provider.RemoteProviderIds
import com.halo.sdk.provider.LocalWorkload
import com.halo.sdk.runtime.Storage
import org.json.JSONObject

internal const val MODE_PROFILES_KEY = "ai_mode_profiles_v1"

/** Only non-secret processing preferences belong to profiles. Credentials never enter this list. */
internal val profileStringKeys: Set<String> = buildSet {
    addAll(listOf("active_ai_provider", "active_ai_model", "ai_routing_policy",
        "speech_stt_provider", "speech_tts_provider", "workload.role.embedding.v1",
        "workspace_image_provider", "workspace_image_model", "elevenlabs_voice_id", "elevenlabs_tts_model_id",
        "elevenlabs_stability", "elevenlabs_similarity_boost", "elevenlabs_speed"))
    AiModelRole.entries.forEach { role ->
        listOf("selection", "provider", "model").forEach { add("role_${role.name}_$it") }
    }
    LocalWorkload.entries.forEach { add("workload.execution.v1.${it.name}") }
    listOf("provider.litert.local", "provider.llamacpp.native", "provider.ollama", "openrouter", "openai", "anthropic", "gemini",
        RemoteProviderIds.GROQ, RemoteProviderIds.CEREBRAS, RemoteProviderIds.XAI,
        RemoteProviderIds.OPENCODE_ZEN, RemoteProviderIds.OPENCODE_COMPANION,
        RemoteProviderIds.CUSTOM_OPENAI_COMPATIBLE).forEach { add("ai_last_model_$it") }
}
internal val profileBooleanKeys: Set<String> = setOf(KEY_CONVERSATION, KEY_DEVICE_STATE,
    KEY_RECENT_EVENTS, KEY_MEMORIES, KEY_TOOLS, KEY_LOCATION, KEY_IMAGES, KEY_AUDIO,
    KEY_VIDEO, KEY_WORKSPACE, KEY_CLOUD_WORKSPACE, KEY_CLOUD_SCREEN, KEY_CLOUD_IMAGES,
    "policy_cloud_memories", "active_ai_selection_migrated_v1", "policy_dynamic_web_extraction")

enum class OnlineFeature { INFERENCE, SPEECH, WEB, IMAGES, CONNECTED_SERVICES }

data class ModeProfile(
    val strings: Map<String, String?>,
    val booleans: Map<String, Boolean>,
    val onlineFeatures: Set<OnlineFeature>,
    val localFallback: Boolean = false,
) {
    val providerId: String? get() = strings["active_ai_provider"]
    val modelId: String? get() = strings["active_ai_model"]
}

/** The same category projection drives disclosure and final dispatch checks. Spending is separate. */
fun ModeProfile.enabledRemoteCategories(): Set<com.halo.sdk.provider.RemoteDataCategory> = buildSet {
    val values = booleans
    fun enabled(key: String) = values[key] == true
    add(com.halo.sdk.provider.RemoteDataCategory.TEXT)
    add(com.halo.sdk.provider.RemoteDataCategory.ACCOUNT_METADATA)
    if (enabled(KEY_CONVERSATION)) add(com.halo.sdk.provider.RemoteDataCategory.CONVERSATION)
    if (enabled(KEY_DEVICE_STATE)) add(com.halo.sdk.provider.RemoteDataCategory.DEVICE_STATE)
    if (enabled(KEY_RECENT_EVENTS)) add(com.halo.sdk.provider.RemoteDataCategory.RECENT_EVENTS)
    if (enabled(KEY_TOOLS)) add(com.halo.sdk.provider.RemoteDataCategory.TOOLS)
    if (enabled(KEY_LOCATION)) add(com.halo.sdk.provider.RemoteDataCategory.LOCATION)
    if (enabled(KEY_IMAGES) && enabled(KEY_CLOUD_IMAGES) && OnlineFeature.IMAGES in onlineFeatures)
        add(com.halo.sdk.provider.RemoteDataCategory.IMAGES)
    if (enabled(KEY_AUDIO)) add(com.halo.sdk.provider.RemoteDataCategory.AUDIO)
    if (enabled(KEY_VIDEO)) add(com.halo.sdk.provider.RemoteDataCategory.VIDEO)
    if (enabled(KEY_WORKSPACE) && enabled(KEY_CLOUD_WORKSPACE)) add(com.halo.sdk.provider.RemoteDataCategory.WORKSPACE)
    if (enabled(KEY_CLOUD_SCREEN)) add(com.halo.sdk.provider.RemoteDataCategory.SCREEN)
    if (enabled(KEY_MEMORIES) && enabled("policy_cloud_memories")) add(com.halo.sdk.provider.RemoteDataCategory.MEMORIES)
}

fun Storage.remoteFeatureEnabled(feature: OnlineFeature): Boolean =
    (this as? ModeProfileStorage)?.allowsFeature(feature) ?: (savedPrivacyMode() != PrivacyMode.PRIVATE)

internal data class ModeProfiles(
    val active: PrivacyMode,
    val privateProfile: ModeProfile,
    val powerProfile: ModeProfile,
    val sharingAcknowledged: Boolean,
    val revision: Long,
) {
    fun profile(mode: PrivacyMode) = if (mode == PrivacyMode.PRIVATE) privateProfile else powerProfile
    fun replacing(mode: PrivacyMode, profile: ModeProfile) = if (mode == PrivacyMode.PRIVATE)
        copy(privateProfile = profile) else copy(powerProfile = profile)

    fun encode(): String = JSONObject().put("schema", 1).put("active", active.name)
        .put("private", privateProfile.encode()).put("power", powerProfile.encode())
        .put("sharingAcknowledged", sharingAcknowledged).put("revision", revision).toString()

    companion object {
        fun decode(raw: String): ModeProfiles {
            require(raw.length <= 262144)
            val json = JSONObject(raw)
            require(json.keys().asSequence().toSet() == setOf("schema", "active", "private", "power", "sharingAcknowledged", "revision"))
            require(json.get("sharingAcknowledged") is Boolean)
            require(json.getInt("schema") == 1)
            val revision = json.getLong("revision").also { require(it >= 0 && it < Long.MAX_VALUE) }
            return ModeProfiles(PrivacyMode.valueOf(json.getString("active")),
                decodeProfile(json.getJSONObject("private"), PrivacyMode.PRIVATE),
                decodeProfile(json.getJSONObject("power"), PrivacyMode.CLOUD),
                json.getBoolean("sharingAcknowledged"), revision)
        }

        fun migrate(storage: Storage): ModeProfiles {
            val active = storage.savedPrivacyMode() ?: PrivacyMode.PRIVATE
            val legacy = defaults(active).let { defaults -> defaults.copy(
                strings = defaults.strings + profileStringKeys.mapNotNull { key ->
                    storage.getString(key)?.let { key to it }
                },
                localFallback = active == PrivacyMode.CLOUD && storage.getString("ai_routing_policy") == "PREFERRED_WITH_FALLBACK",
                booleans = defaults.booleans + profileBooleanKeys.associateWith { key ->
                    storage.getBoolean(key, defaults.booleans[key] ?: false)
                }) }
            return ModeProfiles(active, if (active == PrivacyMode.PRIVATE) legacy else defaults(PrivacyMode.PRIVATE),
                if (active == PrivacyMode.CLOUD) legacy else defaults(PrivacyMode.CLOUD), false, 0)
        }

        fun defaults(mode: PrivacyMode): ModeProfile {
            val cloud = mode == PrivacyMode.CLOUD
            return ModeProfile(mapOf("ai_routing_policy" to if (cloud) "EXPLICIT" else "LOCAL_ONLY"),
                profileBooleanKeys.associateWith { key -> when (key) {
                    "active_ai_selection_migrated_v1" -> false
                    "policy_dynamic_web_extraction" -> cloud
                    KEY_CLOUD_WORKSPACE, KEY_CLOUD_SCREEN, KEY_CLOUD_IMAGES, "policy_cloud_memories" -> cloud
                    else -> true
                } }, if (cloud) OnlineFeature.entries.toSet() else emptySet())
        }

        private fun decodeProfile(json: JSONObject, mode: PrivacyMode): ModeProfile {
            val strings = json.getJSONObject("strings")
            val booleans = json.getJSONObject("booleans")
            require(json.keys().asSequence().toSet() == setOf("strings", "booleans", "onlineFeatures", "localFallback"))
            require(json.get("localFallback") is Boolean)
            require(strings.keys().asSequence().toSet().all { it in profileStringKeys })
            require(booleans.keys().asSequence().toSet() == profileBooleanKeys)
            require(booleans.keys().asSequence().toSet().all { booleans.get(it) is Boolean })
            require(strings.keys().asSequence().toSet().all { strings.isNull(it) || strings.get(it) is String })
            strings.optString("ai_routing_policy").takeIf { it.isNotBlank() }?.let {
                com.halo.sdk.provider.AiRoutingPolicy.valueOf(it)
            }
            val features = json.getJSONArray("onlineFeatures").let { array ->
                (0 until array.length()).map { OnlineFeature.valueOf(array.getString(it)) }.toSet()
            }
            require(mode != PrivacyMode.PRIVATE || features.isEmpty())
            return ModeProfile(strings.keys().asSequence().toSet().associateWith { key ->
                if (strings.isNull(key)) null else strings.getString(key).also { require(it.length <= 16384) }
            }, booleans.keys().asSequence().toSet().associateWith { booleans.getBoolean(it) }, features,
                json.getBoolean("localFallback"))
        }
    }
}

private fun ModeProfile.encode() = JSONObject().put("strings", JSONObject().also { json ->
    strings.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) }
}).put("booleans", JSONObject(booleans)).put("onlineFeatures", org.json.JSONArray(onlineFeatures.map { it.name }))
    .put("localFallback", localFallback)

/** Existing repositories retain ownership; only their preference namespace changes. */
internal class ModeProfileStorage(private val backing: Storage, private val owner: PrivacyModeStore) : Storage by backing,
    com.halo.sdk.provider.RemoteDispatchPolicySource {
    override val remoteDispatchPolicy: com.halo.sdk.provider.RemoteDispatchPolicy get() = owner
    fun contextPolicy() = owner.effectiveContextPolicy()
    fun allowsCategories(provenance: com.halo.sdk.provider.RemoteDispatchProvenance) = owner.permitsRemoteCategories(provenance)
    fun allowsFeature(feature: OnlineFeature) = owner.mode.value == PrivacyMode.CLOUD && owner.sharingAcknowledged &&
        feature in owner.profile(PrivacyMode.CLOUD).onlineFeatures
    override fun getString(key: String, defaultValue: String?): String? = when {
        key == PRIVACY_MODE_KEY -> owner.activeMode().name
        key in profileStringKeys -> owner.activeProfile().strings[key] ?: defaultValue
        else -> backing.getString(key, defaultValue)
    }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = if (key in profileBooleanKeys)
        owner.activeProfile().booleans[key] ?: defaultValue else backing.getBoolean(key, defaultValue)
    override fun putString(key: String, value: String?) = putBatch(strings = mapOf(key to value))
    override fun putBoolean(key: String, value: Boolean) = putBatch(booleans = mapOf(key to value))
    override fun putBatch(strings: Map<String, String?>, booleans: Map<String, Boolean>) {
        val scopedStrings = strings.filterKeys { it in profileStringKeys }
        val scopedBooleans = booleans.filterKeys { it in profileBooleanKeys }
        // Cross-owner writes cannot pretend to be atomic. Commit unrelated settings first;
        // failure never publishes a changed processing profile.
        val globalStrings = strings - scopedStrings.keys
        val globalBooleans = booleans - scopedBooleans.keys
        if (globalStrings.isNotEmpty() || globalBooleans.isNotEmpty()) backing.putBatch(globalStrings, globalBooleans)
        if (com.halo.sdk.provider.PaidMediaPolicy.CONSENT_KEY in globalBooleans ||
            globalStrings.keys.any(::isRemoteAuthorityKey)) owner.invalidateAuthority()
        if (scopedStrings.isNotEmpty() || scopedBooleans.isNotEmpty()) owner.editActive { profile ->
            profile.copy(strings = profile.strings + scopedStrings, booleans = profile.booleans + scopedBooleans)
        }
    }
    override fun remove(key: String) {
        when (key) {
            in profileStringKeys -> owner.editActive { it.copy(strings = it.strings - key) }
            in profileBooleanKeys -> owner.editActive { it.copy(booleans = it.booleans + (key to false)) }
            else -> { backing.remove(key); if (isRemoteAuthorityKey(key)) owner.invalidateAuthority() }
        }
    }
    override fun clear() = owner.clearAll()
}

private fun isRemoteAuthorityKey(key: String) = key in com.halo.app.security.SecretMigrationV2.SECRET_KEYS ||
    key in setOf("ollama_endpoint", "custom_openai_compatible_endpoint", "opencode_companion_endpoint")
