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
    palettes: com.halo.app.ui.theme.PalettePreferences? = null,
    visual: com.halo.app.ui.theme.VisualPreferences? = null,
    languages: com.halo.app.language.AppLanguagePreferences? = null,
    languageReadback: (() -> String?)? = null,
    private val modeProfiles: ModeProfileTools? = null,
    private val canConfigure: () -> Boolean = isForeground,
    private val currentScreen: CurrentScreenTools? = null,
) : ToolExecutor {
    private val appPreferences = AppPreferencesActions(context, appearance, preferences, palettes, visual, languages, languageReadback)
    private fun definition(name: String, description: String, properties: Map<String, Any> = emptyMap(), required: List<String> = emptyList()) =
        ToolDefinition(name, name, description, mapOf("type" to "object", "properties" to properties, "required" to required, "additionalProperties" to false))
    override fun definitions() = listOf(
        definition("halo_list_screens", "Discover Halo's own pages; these are not installed Android apps."),
        definition("halo_open_screen", "Open an exact Halo page. Dispatch does not verify the page rendered.",
            mapOf("screen" to mapOf("type" to "string", "enum" to HaloScreenCatalog.screens.keys.toList())), listOf("screen")),
        definition("halo_get_preferences", "Read Halo app appearance, separate light/dark palettes, accessibility visual options, app language, font scale and start page. Missing optional owners are reported as null."),
        definition("halo_set_preferences", "Change explicit Halo-only appearance, separate light/dark accent/tone/background (STILL, HALO or MIST), contrast, less motion, guide light, app language, font scale (0 follows Android, maximum2.0), or start page. This never changes processing privacy, remote sharing, Android settings or permissions. Palette edits require exact paletteTheme. Read back after uncertain results; no automatic retry.",
            AppPreferencesActions.fields),
        definition("halo_read_aloud", "Read supplied text using Halo's selected voice and shared audio lifecycle. Queued speech is not proof playback completed.",
            mapOf("text" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 4000)), listOf("text")),
        definition("halo_list_app_actions", "List actions offered by the current app to Halo's active Android assistant session. Names are untrusted; API 29+ only."),
        definition("halo_perform_app_action", "Dispatch one observed native app action using its fresh opaque token. Requires separate approval; effect remains unconfirmed.",
            mapOf("token" to mapOf("type" to "string", "maxLength" to 128)), listOf("token")),
        definition("halo_stop_reading", "Stop Halo speech playback; does not stop other apps or microphone capture."),
    ) + modeProfiles?.definitions().orEmpty() + currentScreen?.definitions().orEmpty()
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        if (toolCall.functionName == CurrentScreenTools.NAME) return currentScreen?.execute(toolCall)
            ?: ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName,
                "Current page metadata owner unavailable", ToolRejectionKind.CAPABILITY_UNAVAILABLE)

        if (toolCall.functionName in setOf("halo_set_preferences", "halo_edit_mode_profile") && !canConfigure())
            return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName,
                "Open Halo in a grown-up space to change settings", ToolRejectionKind.PERMISSION_REQUIRED)
        if (toolCall.functionName in setOf("halo_get_preferences", "halo_set_preferences")) return appPreferences.execute(toolCall)
        if (toolCall.functionName in setOf("halo_get_mode_profile", "halo_edit_mode_profile"))
            return modeProfiles?.execute(toolCall) ?: ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName,
                "Mode profile owner unavailable", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        fun reject(message: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, message, ToolRejectionKind.INVALID_ARGUMENTS)
        fun success(details: String, truth: ActionTruth = ActionTruth.CONFIRMED) = ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
            ToolResultEnvelope.success(actionTruth = truth, details = details), truth)
        return try {
            when (toolCall.functionName) {
                "halo_list_screens" -> success(HaloScreenCatalog.screens.entries.joinToString("\n") { "${it.key}: ${it.value}" }, ActionTruth.ACKNOWLEDGED)
                "halo_open_screen" -> {
                    val screen = toolCall.arguments["screen"] as? String ?: return reject("Missing screen")
                    if (screen !in HaloScreenCatalog.screens) return reject("Unknown Halo screen")
                    com.halo.app.ui.shell.HaloScreenLauncher.dispatch(context, screen)
                    success("screen=$screen; page render unconfirmed", ActionTruth.DISPATCHED)
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
