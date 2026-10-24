package com.halo.app.di.tools.host

import com.halo.app.audio.*
import com.halo.app.ui.screens.settings.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class VoiceAdditionalState(val kokoroVoiceId: Int? = null, val kokoroVersion: String? = null,
    val kokoroVoiceCount: Int = 0, val scopedControlAction: String? = null, val controlRevision: Long = 0,
    val followWords: Boolean? = null, val autoReadAnswers: Boolean? = null, val readingSpeed: Double? = null,
    val wearNearbyAvailable: Boolean = false)
data class VoiceConfigurationState(val locale: String, val experience: VoiceExperienceSettings,
    val synthesisFallback: Boolean, val locales: List<String>, val additional: VoiceAdditionalState = VoiceAdditionalState(),
    val busy: Boolean = false, val sourceRevision: Long = 0, val recognitionProvider: String = "AUTOMATIC",
    val recognitionChoices: List<String> = emptyList(), val synthesisProvider: String? = null, val synthesisChoices: List<String> = emptyList())
data class VoiceConfigurationChange(val locale: String? = null, val wearablePhoneFallback: Boolean? = null,
    val spokenInterruptionEnabled: Boolean? = null, val interruptionSensitivity: InterruptionSensitivity? = null,
    val semanticInterruptionEnabled: Boolean? = null, val streamingSpeechEnabled: Boolean? = null,
    val responseStyle: SpokenResponseStyle? = null, val useConnectedAudioAccessories: Boolean? = null,
    val wearNearbyInteraction: Boolean? = null, val synthesisFallback: Boolean? = null, val kokoroVoiceId: Int? = null,
    val scopedControlAction: String? = null, val resetCustomControls: Boolean? = null,
    val followWords: Boolean? = null, val autoReadAnswers: Boolean? = null, val readingSpeed: Double? = null)

/** Metadata + settings adapter only: it exposes no capture, synthesis, playback or audible-acceptance operation. */
class VoiceConfigurationTools(
    private val readState: suspend () -> VoiceConfigurationState,
    private val apply: suspend (VoiceConfigurationState, VoiceConfigurationChange) -> Unit,
    private val isForeground: () -> Boolean,
    private val grownUpAllowed: () -> Boolean,
) : ToolExecutor {
    private constructor(owner: LiveVoiceConfigurationAccess, foreground: () -> Boolean, adult: () -> Boolean) :
        this(owner::read, owner::apply, foreground, adult)
    constructor(storage: Storage, voiceSettings: VoiceSettingsViewModel, language: SpeechLanguageSettingsRepository,
        experience: VoiceExperienceSettingsRepository, isForeground: () -> Boolean, grownUpAllowed: () -> Boolean,
        extras: VoiceConfigurationExtras = VoiceConfigurationExtras(), sourceRevision: () -> Long = { 0L }) :
        this(LiveVoiceConfigurationAccess(storage, voiceSettings, language, experience, extras, sourceRevision), isForeground, grownUpAllowed)

    companion object {
        private val booleanFields = setOf("wearablePhoneFallback", "spokenInterruptionEnabled", "semanticInterruptionEnabled",
            "streamingSpeechEnabled", "useConnectedAudioAccessories", "wearNearbyInteraction", "synthesisFallback",
            "resetCustomControls", "followWords", "autoReadAnswers")
        private val controls = setOf("SystemDefault", "StopSpeaking", "RepeatLastAnswer", "DoNothing")
        private val fields: Map<String, Any> = booleanFields.associateWith { mapOf("type" to "boolean") } + mapOf(
            "revision" to mapOf("type" to "integer", "minimum" to 0),
            "snapshot" to mapOf("type" to "string", "pattern" to "^[a-f0-9]{64}$"),
            "locale" to mapOf("type" to "string", "minLength" to 2, "maxLength" to 16),
            "interruptionSensitivity" to mapOf("type" to "string", "enum" to InterruptionSensitivity.entries.map { it.name }),
            "responseStyle" to mapOf("type" to "string", "enum" to SpokenResponseStyle.entries.map { it.name }),
            "kokoroVoiceId" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 102),
            "scopedControlAction" to mapOf("type" to "string", "enum" to controls.toList()),
            "readingSpeed" to mapOf("type" to "number", "enum" to listOf(.75, 1.0, 1.25, 1.5)))
        private val resultSchema: Map<String, Any> get() {
            val configuration = (booleanFields - setOf("resetCustomControls", "followWords", "autoReadAnswers")).associateWith { mapOf("type" to "boolean") } + mapOf(
                "locale" to mapOf("type" to "string", "maxLength" to 16),
                "interruptionSensitivity" to fields.getValue("interruptionSensitivity"), "responseStyle" to fields.getValue("responseStyle"),
                "kokoroVoiceId" to mapOf("type" to listOf("integer", "null")), "kokoroVersion" to mapOf("type" to listOf("string", "null"), "maxLength" to 200),
                "scopedControlAction" to mapOf("type" to listOf("string", "null"), "maxLength" to 40),
                "controlRevision" to mapOf("type" to "integer"), "followWords" to mapOf("type" to listOf("boolean", "null")),
                "autoReadAnswers" to mapOf("type" to listOf("boolean", "null")), "readingSpeed" to mapOf("type" to listOf("number", "null")),
                "busy" to mapOf("type" to "boolean"), "sourceRevision" to mapOf("type" to "integer"),
                "recognitionProvider" to mapOf("type" to "string", "maxLength" to 200),
                "synthesisProvider" to mapOf("type" to listOf("string", "null"), "maxLength" to 200))
            fun array(max: Int, item: Map<String, Any>) = mapOf("type" to "array", "maxItems" to max, "items" to item)
            val choices = mapOf("locales" to array(32, mapOf("type" to "string", "maxLength" to 16)),
                "interruptionSensitivity" to array(3, mapOf("type" to "string", "enum" to InterruptionSensitivity.entries.map { it.name })),
                "responseStyle" to array(SpokenResponseStyle.entries.size, mapOf("type" to "string", "enum" to SpokenResponseStyle.entries.map { it.name })),
                "kokoroVoiceIds" to array(103, mapOf("type" to "integer", "minimum" to 0, "maximum" to 102)),
                "scopedControlActions" to array(4, mapOf("type" to "string", "enum" to controls.toList())),
                "readingSpeeds" to array(4, mapOf("type" to "number", "enum" to listOf(.75, 1.0, 1.25, 1.5))),
                "recognitionProviderIds" to array(16, mapOf("type" to "string", "maxLength" to 200)),
                "synthesisProviderCatalogueIds" to array(16, mapOf("type" to "string", "maxLength" to 200)),
                "wearNearbyAvailable" to mapOf("type" to "boolean"))
            return mapOf("type" to "object", "properties" to mapOf("revision" to fields.getValue("revision"), "snapshot" to fields.getValue("snapshot"),
                "configuration" to mapOf("type" to "object", "properties" to configuration, "additionalProperties" to false),
                "choices" to mapOf("type" to "object", "properties" to choices, "additionalProperties" to false),
                "playbackStarted" to mapOf("type" to "boolean"), "captureStarted" to mapOf("type" to "boolean")),
                "required" to listOf("revision", "snapshot", "configuration", "choices", "playbackStarted", "captureStarted"), "additionalProperties" to false)
        }
        fun operability(name: String): ToolOperability? = if (name !in setOf("halo_get_voice_configuration", "halo_set_voice_configuration")) null else
            ToolOperability(capabilityId = CapabilityId("host.voice.configuration"), owner = "SpeechLanguageSettingsRepository/VoiceExperienceSettingsRepository/VoiceSettingsViewModel/voice preference owners",
                operation = if (name == "halo_get_voice_configuration") ToolOperation.QUERY else ToolOperation.EDIT,
                resultEncoding = ToolResultEncoding.JSON, resultSchema = resultSchema,
                sourceVersionArguments = if (name == "halo_set_voice_configuration") setOf("revision", "snapshot") else emptySet(),
                limitations = setOf("Exact ephemeral configuration snapshot; no capture/test/playback, OS grants, cloud selection or hearing qualification.",
                    "Foreground/grown-up and existing confirmation owners remain mandatory; uncertain multi-owner writes are not replayed."))
    }
    private val lock = Mutex()
    private val epoch = UUID.randomUUID().toString()
    private var lastHash: String? = null
    private var revision = 0L
    private fun stateJson(state: VoiceConfigurationState): JSONObject = JSONObject().apply {
        val v = state.experience; val a = state.additional
        put("locale", state.locale).put("wearablePhoneFallback", v.wearablePhoneFallback)
            .put("spokenInterruptionEnabled", v.spokenInterruptionEnabled).put("interruptionSensitivity", v.interruptionSensitivity.name)
            .put("semanticInterruptionEnabled", v.semanticInterruptionEnabled).put("streamingSpeechEnabled", v.streamingSpeechEnabled)
            .put("responseStyle", v.responseStyle.name).put("useConnectedAudioAccessories", v.useConnectedAudioAccessories)
            .put("wearNearbyInteraction", v.wearNearbyInteraction).put("synthesisFallback", state.synthesisFallback)
            .put("kokoroVoiceId", a.kokoroVoiceId ?: JSONObject.NULL).put("kokoroVersion", a.kokoroVersion ?: JSONObject.NULL)
            .put("scopedControlAction", a.scopedControlAction ?: JSONObject.NULL).put("controlRevision", a.controlRevision)
            .put("followWords", a.followWords ?: JSONObject.NULL).put("autoReadAnswers", a.autoReadAnswers ?: JSONObject.NULL)
            .put("readingSpeed", a.readingSpeed ?: JSONObject.NULL).put("busy", state.busy).put("sourceRevision", state.sourceRevision)
            .put("recognitionProvider", state.recognitionProvider.take(200)).put("synthesisProvider", state.synthesisProvider?.take(200) ?: JSONObject.NULL)
    }
    private fun witness(state: VoiceConfigurationState): String {
        val json = stateJson(state)
        val canonical = json.keys().asSequence().toList().sorted().joinToString("\n") { "$it=${json.get(it)}" } +
            "\n" + state.locales.sorted().joinToString(",") + "\n" + state.recognitionChoices.sorted().joinToString(",") +
            "\n" + state.synthesisChoices.sorted().joinToString(",") + "\n${state.additional.kokoroVoiceCount}:${state.additional.wearNearbyAvailable}:$epoch"
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    private fun response(state: VoiceConfigurationState, hash: String): JSONObject {
        if (hash != lastHash) { revision++; lastHash = hash }
        val a = state.additional
        return JSONObject().put("revision", revision).put("snapshot", hash).put("configuration", stateJson(state))
            .put("choices", JSONObject().put("locales", JSONArray(state.locales.take(32)))
                .put("interruptionSensitivity", JSONArray(InterruptionSensitivity.entries.map { it.name }))
                .put("responseStyle", JSONArray(SpokenResponseStyle.entries.map { it.name }))
                .put("kokoroVoiceIds", JSONArray((0 until a.kokoroVoiceCount.coerceIn(0, 103)).toList()))
                .put("scopedControlActions", JSONArray(if (a.scopedControlAction == null) emptyList<String>() else controls.toList()))
                .put("readingSpeeds", JSONArray(if (a.readingSpeed == null) emptyList<Double>() else listOf(.75, 1.0, 1.25, 1.5)))
                .put("recognitionProviderIds", JSONArray(state.recognitionChoices.take(16)))
                .put("synthesisProviderCatalogueIds", JSONArray(state.synthesisChoices.take(16)))
                .put("wearNearbyAvailable", a.wearNearbyAvailable))
            .put("captureStarted", false).put("playbackStarted", false)
    }
    private fun requireReadback(change: VoiceConfigurationChange, state: VoiceConfigurationState) {
        val v = state.experience; val a = state.additional
        change.locale?.let { check(state.locale == it) }
        change.wearablePhoneFallback?.let { check(v.wearablePhoneFallback == it) }
        change.spokenInterruptionEnabled?.let { check(v.spokenInterruptionEnabled == it) }
        change.interruptionSensitivity?.let { check(v.interruptionSensitivity == it) }
        change.semanticInterruptionEnabled?.let { check(v.semanticInterruptionEnabled == it) }
        change.streamingSpeechEnabled?.let { check(v.streamingSpeechEnabled == it) }
        change.responseStyle?.let { check(v.responseStyle == it) }
        change.useConnectedAudioAccessories?.let { check(v.useConnectedAudioAccessories == it) }
        change.wearNearbyInteraction?.let { check(v.wearNearbyInteraction == it) }
        change.synthesisFallback?.let { check(state.synthesisFallback == it) }
        change.kokoroVoiceId?.let { check(a.kokoroVoiceId == it) }
        change.scopedControlAction?.let { check(a.scopedControlAction == it) }
        if (change.resetCustomControls == true) check(a.scopedControlAction == "SystemDefault")
        change.followWords?.let { check(a.followWords == it) }
        change.autoReadAnswers?.let { check(a.autoReadAnswers == it) }
        change.readingSpeed?.let { check(a.readingSpeed == it) }
    }
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = lock.withLock {
        fun reject(reason: String, kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        val args = toolCall.arguments
        val reading = toolCall.functionName == "halo_get_voice_configuration"
        if (!reading && toolCall.functionName != "halo_set_voice_configuration") return@withLock reject("Unknown voice settings operation", ToolRejectionKind.TOOL_UNKNOWN)
        if (reading && args.isNotEmpty() || !reading && (args.keys.any { it !in fields } || !args.keys.containsAll(setOf("revision", "snapshot"))))
            return@withLock reject("Use only closed voice-configuration fields")
        var attempted = false
        try {
            val before = readState(); val hash = witness(before)
            val current = response(before, hash)
            if (reading) return@withLock ToolExecutionResult.Success(toolCall.id, toolCall.functionName, current.toString(), ActionTruth.ACKNOWLEDGED)
            if (!isForeground() || !grownUpAllowed()) return@withLock reject("Open Halo in a grown-up session before changing voice settings", ToolRejectionKind.PERMISSION_REQUIRED)
            val expected = args["revision"] as? Number
            if (expected == null || expected.toDouble() != revision.toDouble() || args["snapshot"] != hash)
                return@withLock reject("Voice settings changed. Read them again before a fresh edit.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
            if (before.busy) return@withLock reject("Finish the existing voice test/download before changing settings", ToolRejectionKind.RESOURCE_UNAVAILABLE)
            if (args.keys.size <= 2 || booleanFields.any { it in args && args[it] !is Boolean }) return@withLock reject("Supply explicit correctly typed configuration changes")
            val locale = args["locale"] as? String
            if ("locale" in args && locale !in before.locales) return@withLock reject("Choose an available speech locale")
            val timing = (args["interruptionSensitivity"] as? String)?.let { raw -> InterruptionSensitivity.entries.firstOrNull { it.name == raw } }
            val style = (args["responseStyle"] as? String)?.let { raw -> SpokenResponseStyle.entries.firstOrNull { it.name == raw } }
            if ("interruptionSensitivity" in args && timing == null || "responseStyle" in args && style == null) return@withLock reject("Unknown voice experience choice")
            val voice = (args["kokoroVoiceId"] as? Number)?.toInt()
            if ("kokoroVoiceId" in args && (voice == null || (args["kokoroVoiceId"] as Number).toDouble() != voice.toDouble() || voice !in 0 until before.additional.kokoroVoiceCount))
                return@withLock reject("Choose a voice from the actually installed local pack", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
            val action = args["scopedControlAction"] as? String
            if ("scopedControlAction" in args && (action !in controls || before.additional.scopedControlAction == null) || "resetCustomControls" in args && (args["resetCustomControls"] != true || before.additional.scopedControlAction == null))
                return@withLock reject("Scoped speech controls are unavailable or unsupported")
            if (action != null && "resetCustomControls" in args) return@withLock reject("Choose either a control action or reset")
            val speed = (args["readingSpeed"] as? Number)?.toDouble()
            if ("readingSpeed" in args && (speed !in listOf(.75, 1.0, 1.25, 1.5) || before.additional.readingSpeed == null) ||
                "followWords" in args && before.additional.followWords == null || "autoReadAnswers" in args && before.additional.autoReadAnswers == null ||
                "wearNearbyInteraction" in args && !before.additional.wearNearbyAvailable) return@withLock reject("The requested voice preference owner or choice is unavailable", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
            val change = VoiceConfigurationChange(locale, args["wearablePhoneFallback"] as? Boolean, args["spokenInterruptionEnabled"] as? Boolean,
                timing, args["semanticInterruptionEnabled"] as? Boolean, args["streamingSpeechEnabled"] as? Boolean, style,
                args["useConnectedAudioAccessories"] as? Boolean, args["wearNearbyInteraction"] as? Boolean, args["synthesisFallback"] as? Boolean,
                voice, action, args["resetCustomControls"] as? Boolean, args["followWords"] as? Boolean, args["autoReadAnswers"] as? Boolean, speed)
            check(witness(readState()) == hash) { "Voice settings changed" }
            attempted = true
            apply(before, change)
            val saved = readState()
            requireReadback(change, saved)
            return@withLock ToolExecutionResult.Success(toolCall.id, toolCall.functionName, response(saved, witness(saved)).toString(), ActionTruth.CONFIRMED)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@withLock if (attempted) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
            "Some voice settings may have saved. Read current settings before a fresh request; no automatic replay.", completionUnknown = true)
        else reject("Voice configuration unavailable or changed", ToolRejectionKind.RESOURCE_UNAVAILABLE) }
    }
    override fun definitions() = listOf(
        ToolDefinition("halo_get_voice_configuration", "halo_get_voice_configuration", "Read bounded real voice settings and currently offered locale/local voice/control choices with a fresh ephemeral revision/snapshot. Installed/configured choices do not prove recognition, audible playback or connected hardware. Never starts a microphone, playback, download or provider request.",
            mapOf("type" to "object", "properties" to emptyMap<String, Any>(), "additionalProperties" to false)),
        ToolDefinition("halo_set_voice_configuration", "halo_set_voice_configuration", "After approval change only explicit voice settings at the exact observed revision/snapshot through existing settings owners. Foreground and grown-up authority required. No capture, test playback, audible acceptance, OS grants, remote voice activation or paid consent. Missing setup/busy state rejects; uncertain partial writes require readback, never replay.",
            mapOf("type" to "object", "properties" to fields, "required" to listOf("revision", "snapshot"), "additionalProperties" to false)))
}
