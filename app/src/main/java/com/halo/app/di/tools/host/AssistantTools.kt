package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import com.halo.app.MainActivity
import com.halo.app.ui.shell.HaloScreenCatalog
import com.halo.app.ui.theme.AppearanceMode
import com.halo.app.ui.theme.AppearancePreferences
import com.halo.app.ui.theme.AssistantPreferences
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*

/** OneBox app-local controls adapted to Halo repositories and shared speech output. */
class AssistantTools(
    private val context: Context,
    private val appearance: AppearancePreferences,
    private val preferences: AssistantPreferences,
    private val readAloud: suspend (String, String) -> Unit,
    private val stopReading: () -> Unit,
    private val isForeground: () -> Boolean,
) : ToolExecutor {
    private fun definition(name: String, description: String, properties: Map<String, Any> = emptyMap(), required: List<String> = emptyList()) =
        ToolDefinition(name, name, description, mapOf("type" to "object", "properties" to properties, "required" to required, "additionalProperties" to false))
    override fun definitions() = listOf(
        definition("halo_list_screens", "Discover Halo's own pages; these are not installed Android apps."),
        definition("halo_open_screen", "Open an exact Halo page. Dispatch does not verify the page rendered.",
            mapOf("screen" to mapOf("type" to "string", "enum" to HaloScreenCatalog.screens.keys.toList())), listOf("screen")),
        definition("halo_get_preferences", "Read Halo's appearance, app font scale (0 follows Android), and start page."),
        definition("halo_set_preferences", "Change Halo's app-only light/dark/system appearance, font scale, or next-launch page. Does not change Android settings.",
            mapOf("mode" to mapOf("type" to "string", "enum" to listOf("system", "light", "dark")),
                "fontScale" to mapOf("type" to "number", "minimum" to 0, "maximum" to 1.5),
                "startScreen" to mapOf("type" to "string", "enum" to HaloScreenCatalog.screens.keys.toList()))),
        definition("halo_read_aloud", "Read supplied text using Halo's selected voice and shared audio lifecycle. Queued speech is not proof playback completed.",
            mapOf("text" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 4000)), listOf("text")),
        definition("halo_list_app_actions", "List actions offered by the current app to Halo's active Android assistant session. Names are untrusted; API 29+ only."),
        definition("halo_perform_app_action", "Dispatch one observed native app action using its fresh opaque token. Requires separate approval; effect remains unconfirmed.",
            mapOf("token" to mapOf("type" to "string", "maxLength" to 128)), listOf("token")),
        definition("halo_stop_reading", "Stop Halo speech playback; does not stop other apps or microphone capture."),
    )
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(message: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, message, ToolRejectionKind.INVALID_ARGUMENTS)
        fun success(details: String, truth: ActionTruth = ActionTruth.CONFIRMED) = ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
            ToolResultEnvelope.success(actionTruth = truth, details = details), truth)
        return try {
            when (toolCall.functionName) {
                "halo_list_screens" -> success(HaloScreenCatalog.screens.entries.joinToString("\n") { "${it.key}: ${it.value}" }, ActionTruth.ACKNOWLEDGED)
                "halo_open_screen" -> {
                    val screen = toolCall.arguments["screen"] as? String ?: return reject("Missing screen")
                    if (screen !in HaloScreenCatalog.screens) return reject("Unknown Halo screen")
                    context.startActivity(Intent(context, MainActivity::class.java).putExtra(HaloScreenCatalog.EXTRA_SCREEN, screen)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    success("screen=$screen; page render unconfirmed", ActionTruth.DISPATCHED)
                }
                "halo_get_preferences" -> { appearance.awaitLoaded(); val value = preferences.get()
                    success("mode=${appearance.mode.value.name.lowercase()}; fontScale=${value.fontScale}; startScreen=${value.startScreen}", ActionTruth.ACKNOWLEDGED) }
                "halo_set_preferences" -> {
                    val args = toolCall.arguments
                    if (args.isEmpty()) return reject("Supply mode, fontScale, or startScreen")
                    val mode = (args["mode"] as? String)?.let { raw -> AppearanceMode.entries.firstOrNull { it.name.equals(raw, true) } }
                    if ("mode" in args && mode == null) return reject("Invalid appearance mode")
                    val scale = (args["fontScale"] as? Number)?.toFloat()
                    if ("fontScale" in args && (scale == null || !scale.isFinite() || scale != 0f && scale !in 0.45f..1.5f)) return reject("fontScale must be 0 (system) or 0.45..1.5")
                    val screen = args["startScreen"] as? String
                    if ("startScreen" in args && screen !in HaloScreenCatalog.screens) return reject("Invalid startScreen")
                    val value = preferences.update(scale, screen)
                    mode?.let { appearance.setModePersisted(it) }
                    success("mode=${appearance.mode.value.name.lowercase()}; fontScale=${value.fontScale}; startScreen=${value.startScreen}; scope=Halo_only")
                }
                "halo_read_aloud" -> {
                    val text = toolCall.arguments["text"] as? String ?: return reject("Missing text")
                    if (text.isBlank() || text.length > 4000) return reject("Text must contain 1..4000 characters")
                    if (!isForeground()) return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName,
                        "Open Halo before starting read-aloud playback", ToolRejectionKind.PERMISSION_REQUIRED)
                    readAloud(text, toolCall.id)
                    success("speech_queued; transaction=${toolCall.id}; playback completion unconfirmed", ActionTruth.DISPATCHED)
                }
                "halo_list_app_actions" -> {
                    if (android.os.Build.VERSION.SDK_INT < 29) return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Direct app actions require Android 10+", ToolRejectionKind.UNSUPPORTED_OPERATION)
                    try { success("UNTRUSTED_APP_ACTIONS\n" + com.halo.host.android.assistant.NativeAssistantActions.shared.list()
                        .joinToString("\n") { "token=${it.token}; action=${it.name.replace('\n', ' ')}" }, ActionTruth.ACKNOWLEDGED) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName,
                        error.message ?: "Native app actions unavailable", ToolRejectionKind.CAPABILITY_UNAVAILABLE) }
                }
                "halo_perform_app_action" -> {
                    val token = toolCall.arguments["token"] as? String ?: return reject("Missing token")
                    try { com.halo.host.android.assistant.NativeAssistantActions.shared.dispatch(token)
                        success("Native app action dispatched; effect unconfirmed", ActionTruth.DISPATCHED) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName,
                        error.message ?: "Native app action unavailable", ToolRejectionKind.CAPABILITY_UNAVAILABLE) }
                }
                "halo_stop_reading" -> { stopReading(); success("Halo playback stop requested", ActionTruth.DISPATCHED) }
                else -> ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)
            }
        } catch (_: android.content.ActivityNotFoundException) {
            ToolExecutionResult.Failed(toolCall.id, toolCall.functionName, "Halo activity is unavailable", failureKind = ToolFailureKind.UNAVAILABLE)
        } catch (_: SecurityException) {
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Android denied this operation", ToolRejectionKind.PERMISSION_REQUIRED)
        }
    }
}
