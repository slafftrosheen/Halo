package com.halo.app.di.tools

import com.halo.app.ui.screens.settings.VoiceSettingsViewModel
import com.halo.providers.sherpa.stt.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class SpeechAssetMetadata(val modelId: String, val name: String, val providerId: String,
    val assetVersion: String, val installed: Boolean, val expectedBytes: Long, val supportsLanguage: Boolean,
    val artifactVersion: String? = null)
data class SpeechInventorySnapshot(val sourceVersion: String, val selectedProviderId: String, val residentModelId: String?,
    val language: String, val testing: Boolean, val downloadingModelId: String?, val downloading: Boolean,
    val verifying: Boolean, val downloadedBytes: Long, val expectedBytes: Long, val failedModelId: String?,
    val failed: Boolean, val assets: List<SpeechAssetMetadata>)
interface SpeechInventoryControl {
    suspend fun current(): SpeechInventorySnapshot
    suspend fun dispatch(modelId: String, sourceVersion: String, assetVersion: String, action: String,
        admitted: () -> Boolean, artifactVersion: String? = null): String
}

/** Adapter to the exact Voice UI owner. It introduces no downloader, URLs, file paths or catalog entries. */
class VoiceSpeechInventoryControl(private val inventory: SpeechModelInventoryRepository,
    private val voice: VoiceSettingsViewModel, private val isCaptureActive: () -> Boolean = { true }) : SpeechInventoryControl {
    private val lock = Mutex()
    private val lifetime = UUID.randomUUID().toString()
    private var epoch = 0L
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private val specs get() = SherpaSpeechModelCatalog.recognitionModels + SherpaSpeechModelCatalog.sileroVad
    private suspend fun read(): SpeechInventorySnapshot {
        inventory.refresh()
        check(!inventory.state.value.loading && inventory.state.value.error == null) { "Speech inventory unavailable" }
        val artifactVersions = specs.associate { it.id to inventory.artifactVersion(it.id) }
        return withContext(Dispatchers.Main.immediate) {
            val ui = voice.uiState.value
            val saved = inventory.state.value
            val assets = specs.map { spec -> SpeechAssetMetadata(spec.id, spec.displayName.take(200), spec.providerId,
                "speech-asset:" + digest(spec.toString()),
                if (spec.id == SherpaSpeechModelCatalog.SILERO_VAD_ID) saved.sileroInstalled else saved.models[spec.id] == true,
                spec.expectedBytes, spec.id == SherpaSpeechModelCatalog.SILERO_VAD_ID || spec.supportsLanguage(ui.selectedLocale), artifactVersions[spec.id]) }
            val version = "speech-inventory:" + digest(listOf(lifetime, epoch, ui.selectedProviderId, ui.residentModelId,
                ui.selectedLocale, ui.isTesting, ui.isDownloading, ui.downloadingModelId, ui.verifying, ui.failedModelId,
                ui.downloadError != null, assets.map { it.copy(artifactVersion = null) }).joinToString("\u0000"))
            SpeechInventorySnapshot(version, ui.selectedProviderId, ui.residentModelId, ui.selectedLocale, ui.isTesting,
                ui.downloadingModelId, ui.isDownloading, ui.verifying, ui.downloadedBytes, ui.expectedBytes,
                ui.failedModelId, ui.downloadError != null, assets)
        }
    }
    override suspend fun current(): SpeechInventorySnapshot = lock.withLock { read() }
    override suspend fun dispatch(modelId: String, sourceVersion: String, assetVersion: String, action: String,
        admitted: () -> Boolean, artifactVersion: String?): String = lock.withLock {
        val observed = read()
        check(observed.sourceVersion == sourceVersion) { "Speech inventory changed" }
        val asset = observed.assets.singleOrNull { it.modelId == modelId }
        val automatic = modelId == "AUTOMATIC" && action == "select"
        check(if (automatic) assetVersion == automaticVersion else asset?.assetVersion == assetVersion) { "Pinned speech asset changed" }
        withContext(Dispatchers.Main.immediate) {
            val ui = voice.uiState.value
            check(admitted() && !isCaptureActive() && !ui.isTesting) { "Stop speech capture/testing before changing speech assets" }
            check(ui.selectedProviderId == observed.selectedProviderId && ui.residentModelId == observed.residentModelId &&
                ui.selectedLocale == observed.language && ui.isDownloading == observed.downloading &&
                ui.downloadingModelId == observed.downloadingModelId) { "Speech owner changed" }
            if (action == "pause") check(ui.isDownloading && ui.downloadingModelId == modelId) { "Only this owner's active download may be paused" }
            else check(!ui.isDownloading) { "Finish or pause the current download first" }
            when (action) {
                "install", "resume" -> check(asset != null && !asset.installed) { "Asset already installed" }
                "remove" -> check(asset != null && artifactVersion != null && asset.artifactVersion == artifactVersion &&
                    (modelId == SherpaSpeechModelCatalog.SILERO_VAD_ID || observed.selectedProviderId != asset.providerId && observed.residentModelId != modelId)) { "Select another model or reread the exact artifact first" }
                "select" -> check(automatic || asset != null && asset.providerId.isNotBlank() && asset.installed && asset.supportsLanguage) { "Choose an installed compatible recognition model" }
                "pause" -> Unit
                else -> error("Unsupported speech asset action")
            }
            epoch++ // Consume the observed process token before invoking the native owner.
            try { when (action) {
                "install", "resume" -> voice.installModel(modelId)
                "pause" -> voice.pauseModelDownload()
                "remove" -> voice.removeModelExpected(modelId, requireNotNull(artifactVersion))
                "select" -> voice.selectModel(if (automatic) "AUTOMATIC" else requireNotNull(asset).providerId)
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { throw SpeechInventoryDispatchUncertain(error) }
            UUID.randomUUID().toString()
        }
    }
    companion object { val automaticVersion: String = "speech-asset:" + MessageDigest.getInstance("SHA-256")
        .digest("AUTOMATIC/native-speech-catalog-v1".toByteArray()).joinToString("") { "%02x".format(it) } }
}
private class SpeechInventoryDispatchUncertain(cause: Exception) : Exception(cause)

class SpeechInventoryTools(private val control: SpeechInventoryControl, private val canConfigure: () -> Boolean = { false }) : ToolExecutor {
    constructor(inventory: SpeechModelInventoryRepository, voice: VoiceSettingsViewModel,
        canConfigure: () -> Boolean, isCaptureActive: () -> Boolean) : this(VoiceSpeechInventoryControl(inventory, voice, isCaptureActive), canConfigure)
    override fun definitions() = listOf(
        ToolDefinition("halo_speech_inventory", "halo_speech_inventory", "Read actual local pinned speech model/VAD inventory, exact asset and process source versions, selection and this native Voice owner's download/testing status. Installed files are not recognition/language/device qualification. No remote discovery, arbitrary URL, speech capture or download starts.",
            mapOf("type" to "object", "properties" to emptyMap<String, Any>(), "additionalProperties" to false)),
        ToolDefinition("halo_speech_model_action", "halo_speech_model_action", "After explicit approval dispatch install/resume/pause/remove/select through the existing native Voice inventory/installer owner, with exact observed modelId/sourceVersion/assetVersion. Downloads use only the pinned catalogue and may use substantial network/storage. Pause applies only to that exact Voice-owned download; removal preserves selected/active models and requires capture/testing stopped. AUTOMATIC is select-only. No custom URL/import, provider consent, engine qualification or automatic selection after install. DISPATCHED is not installed/removed/selected; read inventory afterwards. Never replay uncertain actions.",
            mapOf("type" to "object", "properties" to mapOf(
                "modelId" to mapOf("type" to "string", "enum" to (SherpaSpeechModelCatalog.recognitionModels.map { it.id } + listOf(SherpaSpeechModelCatalog.SILERO_VAD_ID, "AUTOMATIC"))),
                "sourceVersion" to mapOf("type" to "string", "pattern" to "^speech-inventory:[a-f0-9]{64}$"),
                "assetVersion" to mapOf("type" to "string", "pattern" to "^speech-asset:[a-f0-9]{64}$"),
                "artifactVersion" to mapOf("type" to "string", "pattern" to "^speech-artifact:[a-f0-9]{64}$"),
                "action" to mapOf("type" to "string", "enum" to listOf("install", "resume", "pause", "remove", "select"))),
                "required" to listOf("modelId", "sourceVersion", "assetVersion", "action"), "additionalProperties" to false)),
    )
    override suspend fun execute(toolCall: ToolCall) = execute(toolCall, ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        var invoking = false
        fun reject(kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName,
            "Speech assets unavailable, active, or changed. Read inventory and stop capture/testing before a fresh decision; never replay uncertain changes.", kind)
        try {
            if (!canConfigure()) return reject(ToolRejectionKind.PERMISSION_REQUIRED)
            val args = toolCall.arguments
            if (toolCall.functionName == "halo_speech_inventory") {
                require(args.isEmpty())
                return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, snapshot(control.current()).toString(), ActionTruth.ACKNOWLEDGED)
            }
            if (toolCall.functionName != "halo_speech_model_action") return reject(ToolRejectionKind.TOOL_UNKNOWN)
            if (context.interactionId.isNullOrBlank()) return reject(ToolRejectionKind.PERMISSION_REQUIRED)
            fun text(key: String) = (args[key] as? String ?: throw IllegalArgumentException()).also { require(it.length in 1..200 && it.none(Char::isISOControl)) }
            val model = text("modelId").also { require(it in SherpaSpeechModelCatalog.recognitionModels.map { spec -> spec.id } + listOf(SherpaSpeechModelCatalog.SILERO_VAD_ID, "AUTOMATIC")) }
            val source = text("sourceVersion").also { require(it.matches(Regex("speech-inventory:[a-f0-9]{64}"))) }
            val asset = text("assetVersion").also { require(it.matches(Regex("speech-asset:[a-f0-9]{64}"))) }
            val action = text("action").also { require(it in setOf("install", "resume", "pause", "remove", "select")) }
            require(args.keys == setOf("modelId", "sourceVersion", "assetVersion", "action") + if (action == "remove") setOf("artifactVersion") else emptySet())
            val artifact = if (action == "remove") text("artifactVersion").also { require(it.matches(Regex("speech-artifact:[a-f0-9]{64}"))) } else null
            invoking = true
            val request = control.dispatch(model, source, asset, action, canConfigure, artifact)
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, JSONObject().put("requestId", request)
                .put("modelId", model).put("action", action).put("status", "DISPATCHED").put("completionConfirmed", false).toString(), ActionTruth.DISPATCHED)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) { return reject() }
        catch (_: IllegalStateException) { return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE) }
        catch (_: Exception) { return if (invoking) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
            "Speech asset dispatch outcome unknown. Read inventory before requesting another change; do not replay.", completionUnknown = true) else reject(ToolRejectionKind.RESOURCE_UNAVAILABLE) }
    }
    private fun snapshot(value: SpeechInventorySnapshot) = JSONObject().put("sourceVersion", value.sourceVersion)
        .put("selectedProviderId", value.selectedProviderId).put("residentModelId", value.residentModelId ?: JSONObject.NULL)
        .put("language", value.language).put("testing", value.testing).put("downloading", value.downloading)
        .put("downloadingModelId", value.downloadingModelId ?: JSONObject.NULL).put("verifying", value.verifying)
        .put("downloadedBytes", value.downloadedBytes).put("expectedBytes", value.expectedBytes).put("failed", value.failed)
        .put("failedModelId", value.failedModelId ?: JSONObject.NULL).put("automaticAssetVersion", VoiceSpeechInventoryControl.automaticVersion)
        .put("qualification", "NOT_TESTED").put("assets", JSONArray(value.assets.take(16).map { JSONObject()
            .put("modelId", it.modelId).put("name", it.name).put("providerId", it.providerId).put("assetVersion", it.assetVersion)
            .put("artifactVersion", it.artifactVersion ?: JSONObject.NULL).put("installed", it.installed)
            .put("expectedBytes", it.expectedBytes).put("supportsLanguageByCatalog", it.supportsLanguage) }))
}
