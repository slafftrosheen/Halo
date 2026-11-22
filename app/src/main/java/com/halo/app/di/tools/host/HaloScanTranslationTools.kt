package com.halo.app.di.tools.host

import com.halo.app.ui.toolkit.HaloScanTranslationSessions
import com.halo.app.ui.toolkit.HaloScanLine
import com.halo.app.ui.toolkit.ScanTranslationReceipt
import com.halo.app.ui.toolkit.scanLanguages
import com.halo.app.ui.toolkit.scanTranslationReady
import com.halo.app.ui.toolkit.translateScanLines
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Saved results are app-owned; tool responses contain identifiers and state only. */
internal class HaloScanTranslationTools(private val canAccess: () -> Boolean) : ToolExecutor {
    override fun definitions() = listOf(
        ToolDefinition(STATUS, STATUS,
            "Read the foreground translated scan's save state and exact token/version. It does not read photo pixels or translated text.",
            schema(emptyMap())),
        ToolDefinition(KEEP, KEEP,
            "Keep the exact visible translated scan as one local PDF in Workspace, with its camera photo and translated text. Use the current token and version from halo_scan_translation_status. Does not start a camera, translate, share or send anything.",
            schema(keepArgs)),
        ToolDefinition(TEXT, TEXT,
            "Translate explicitly provided text with the installed on-device languages. Does not download models or save text.",
            schema(textArgs)),
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: ToolRejectionKind) =
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        val runtime = currentCoroutineContext()[RunToolScope]
        if (runtime?.targetIsLocal != true)
            return reject("Use on-device Halo to control the visible translation.", ToolRejectionKind.PERMISSION_REQUIRED)
        return withContext(Dispatchers.Main.immediate) {
            if (!canAccess()) return@withContext reject("Open the translation on the unlocked adult phone.", ToolRejectionKind.PERMISSION_REQUIRED)
            val draft = HaloScanTranslationSessions.current()
            val current = draft?.state?.value
            when (toolCall.functionName) {
                TEXT -> {
                    val fields = toolCall.arguments
                    val text = fields["text"] as? String
                    val from = (fields["from"] as? String)?.takeIf(languageCodes::contains)
                    val to = (fields["to"] as? String)?.takeIf(languageCodes::contains)
                    if (fields.keys != textArgs.keys || text.isNullOrBlank() || text.length > 4000 || from == null || to == null)
                        return@withContext reject("Use 1 to 4000 characters and supported source and target language codes.", ToolRejectionKind.INVALID_ARGUMENTS)
                    try {
                        if (from != to && !scanTranslationReady(from, to))
                            return@withContext reject("Download the language pair in Translate first.", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
                        val output = translateScanLines(listOf(HaloScanLine(text, null, 0)), from, to, download = false).single().text
                        if (!canAccess()) return@withContext reject("The on-device session changed.", ToolRejectionKind.PERMISSION_REQUIRED)
                        ToolExecutionResult.Success(toolCall.id, TEXT, JSONObject().put("text", output).put("from", from)
                            .put("to", to).toString(), ActionTruth.ACKNOWLEDGED)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { reject("Translation could not finish on this phone.", ToolRejectionKind.CAPABILITY_UNAVAILABLE) }
                }
                STATUS -> if (toolCall.arguments.isEmpty()) success(toolCall, current, ActionTruth.ACKNOWLEDGED)
                    else reject("This query takes no arguments.", ToolRejectionKind.INVALID_ARGUMENTS)
                KEEP -> {
                    val token = toolCall.arguments["sessionToken"] as? String
                    val version = toolCall.arguments["sourceVersion"] as? Number
                    if (toolCall.arguments.keys != keepArgs.keys || token == null || version == null ||
                        !runCatching { UUID.fromString(token).toString() == token }.getOrDefault(false) ||
                        version.toDouble() != version.toLong().toDouble() || version.toLong() < 1)
                        return@withContext reject("Use the exact token and version from translation status.", ToolRejectionKind.INVALID_ARGUMENTS)
                    if (draft == null || current == null || current.sessionToken != token || current.sourceVersion != version.toLong())
                        return@withContext reject("The visible translation changed.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    try {
                        val saved = draft.keep(current)
                            ?: return@withContext reject("The translation is no longer ready to keep.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
                        success(toolCall, saved, ActionTruth.CONFIRMED)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { reject("Could not finish keeping this translation.", ToolRejectionKind.RESOURCE_UNAVAILABLE) }
                }
                else -> reject("Unknown Scan action.", ToolRejectionKind.TOOL_UNKNOWN)
            }
        }
    }

    private fun success(call: ToolCall, value: ScanTranslationReceipt?, truth: ActionTruth) =
        ToolExecutionResult.Success(call.id, call.functionName, JSONObject()
            .put("phase", value?.phase?.name ?: "IDLE")
            .put("sessionToken", value?.sessionToken ?: JSONObject.NULL)
            .put("sourceVersion", value?.sourceVersion ?: 0L)
            .put("objectId", value?.objectId ?: JSONObject.NULL)
            .toString(), truth)

    companion object {
        const val STATUS = "halo_scan_translation_status"
        const val KEEP = "halo_scan_keep_translation"
        const val TEXT = "halo_translate_text"
        val NAMES = setOf(STATUS, KEEP, TEXT)
        private val languageCodes = scanLanguages.map { it.code }
        private val languageCode = mapOf("type" to "string", "enum" to languageCodes)
        private val textArgs = mapOf("text" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 4000),
            "from" to languageCode, "to" to languageCode)
        private val keepArgs = mapOf(
            "sessionToken" to mapOf("type" to "string", "minLength" to 36, "maxLength" to 36),
            "sourceVersion" to mapOf("type" to "integer", "minimum" to 1),
        )
        private fun schema(fields: Map<String, Any>) =
            mapOf("type" to "object", "additionalProperties" to false, "properties" to fields, "required" to fields.keys.toList())

        fun descriptor(definition: ToolDefinition): ToolDescriptor? {
            if (definition.name !in NAMES) return null
            if (definition.name == TEXT) return ToolDescriptor(
                id = ToolId(TEXT), definition = definition, category = ToolCategory.INFORMATION,
                keywords = setOf("translate", "translation", "offline", "перевод", "переведи"),
                security = ToolSecurityPolicy.READ_ONLY,
                operability = ToolOperability(capabilityId = CapabilityId("halo.scan.translation"), owner = "HaloTranslateScreen",
                    operation = ToolOperation.QUERY, resultEncoding = ToolResultEncoding.JSON,
                    resultSchema = schema(mapOf("text" to mapOf("type" to "string"), "from" to languageCode, "to" to languageCode)),
                    limitations = setOf("Local, unlocked adult sessions with the language pair already installed. Use Translate to download languages.")),
            )
            val keep = definition.name == KEEP
            return ToolDescriptor(id = ToolId(definition.name), definition = definition, category = ToolCategory.FILES,
                keywords = setOf("scan", "translate", "translation", "keep", "pdf"),
                security = if (keep) ToolSecurityPolicy(ToolRiskClass.REVERSIBLE, ConfirmationPolicy.USER_POLICY, ReplaySafety.NEVER_REPLAY)
                    else ToolSecurityPolicy.READ_ONLY,
                operability = ToolOperability(capabilityId = CapabilityId("halo.scan.translation"), owner = "HaloScanTranslationDraft",
                    operation = if (keep) ToolOperation.CREATE else ToolOperation.QUERY, resultEncoding = ToolResultEncoding.JSON,
                    sourceVersionArguments = if (keep) keepArgs.keys else emptySet(), resultIdFields = setOf("objectId", "sessionToken"),
                    resultSchema = schema(mapOf(
                        "phase" to mapOf("type" to "string", "enum" to listOf("IDLE", "READY", "KEEPING", "KEPT")),
                        "sessionToken" to mapOf("type" to listOf("string", "null"), "maxLength" to 36),
                        "sourceVersion" to mapOf("type" to "integer", "minimum" to 0),
                        "objectId" to mapOf("type" to listOf("string", "null"), "maxLength" to 160),
                    )), limitations = setOf("On-device assistant and resumed adult Scan only. Camera and translation are user-operated.")))
        }
    }
}
