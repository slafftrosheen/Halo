package com.halo.app.di.tools.host

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.runtime.cognition.tools.ToolFailureKind
import com.halo.runtime.cognition.tools.ToolRejectionKind
import com.halo.runtime.cognition.tools.ToolResultEnvelope
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

/**
 * RUNTIME-056 native ability batch. The settings catalogue was a fixed eight-entry map
 * mined against nothing; the OneBox settings tool exposed the same lesson Halo's app
 * launcher already learned: a catalogue must describe *this* device.
 *
 * Two rules now hold:
 *
 * 1. A section is offered to a model only when an installed activity actually answers
 *    that intent, so the schema never lists a screen the phone cannot open.
 * 2. Where Android requires a package or authority to address a screen, the intent is
 *    built with it instead of opening a generic list (or failing).
 */
class SystemTools(private val context: Context) : ToolExecutor {

    /** [dataFor] is evaluated per device; a null result means "this section is not addressable here". */
    private class Section(
        val action: String,
        val dataFor: ((Context) -> Uri?)? = null,
        val extras: ((Context) -> Map<String, String>)? = null,
    )

    private val sections: Map<String, Section> = buildMap {
        put("general", Section(Settings.ACTION_SETTINGS))
        put("wireless", Section(Settings.ACTION_WIRELESS_SETTINGS))
        put("roaming", Section(Settings.ACTION_DATA_ROAMING_SETTINGS))
        put("sync", Section(Settings.ACTION_SYNC_SETTINGS))
        put("search", Section(Settings.ACTION_SEARCH_SETTINGS))
        put("user_dictionary", Section(Settings.ACTION_USER_DICTIONARY_SETTINGS))
        put("input_method_subtype", Section(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS))
        put("captioning", Section(Settings.ACTION_CAPTIONING_SETTINGS))
        put("printing", Section(Settings.ACTION_PRINT_SETTINGS))
        put("dream", Section(Settings.ACTION_DREAM_SETTINGS))
        put("wifi_ip", Section(Settings.ACTION_WIFI_IP_SETTINGS))
        if (Build.VERSION.SDK_INT >= 29) {
            put("internet_panel", Section(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            put("wifi_panel", Section(Settings.Panel.ACTION_WIFI))
            put("volume_panel", Section(Settings.Panel.ACTION_VOLUME))
            put("nfc_panel", Section(Settings.Panel.ACTION_NFC))
        }
        if (Build.VERSION.SDK_INT >= 30) {
            put("biometric_enrollment", Section(Settings.ACTION_BIOMETRIC_ENROLL))
        }
        put("internet", Section(if (android.os.Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS))
        put("volume", Section(if (android.os.Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS))
        put("wifi", Section(Settings.ACTION_WIFI_SETTINGS))
        put("bluetooth", Section(Settings.ACTION_BLUETOOTH_SETTINGS))
        put("sound", Section(Settings.ACTION_SOUND_SETTINGS))
        put("location", Section(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        put("display", Section(Settings.ACTION_DISPLAY_SETTINGS))
        // Hidden or OEM-convention screens. These are literal action strings because the
        // platform does not publish constants for them; the resolver below filters out any
        // that this device cannot actually open, so an unavailable name simply never
        // reaches a model.
        put("dark_mode", Section("android.settings.DARK_MODE_SETTINGS"))
        put("vibration", Section("android.settings.VIBRATION_SETTINGS"))
        put("private_dns", Section("android.settings.PRIVATE_DNS_SETTINGS"))
        put("picture_in_picture", Section("android.settings.PICTURE_IN_PICTURE_SETTINGS"))
        put("companion_devices", Section("android.settings.MANAGE_COMPANION_DEVICES"))
        put("storage", Section(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        put("battery", Section(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        put("battery_optimization", Section(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        put("data_usage", Section(Settings.ACTION_DATA_USAGE_SETTINGS))
        put("airplane", Section(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
        put("security", Section(Settings.ACTION_SECURITY_SETTINGS))
        put("privacy", Section(Settings.ACTION_PRIVACY_SETTINGS))
        put("accessibility", Section(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        put("language", Section(Settings.ACTION_LOCALE_SETTINGS))
        put("date_time", Section(Settings.ACTION_DATE_SETTINGS))
        put("keyboard", Section(Settings.ACTION_INPUT_METHOD_SETTINGS))
        put("nfc", Section(Settings.ACTION_NFC_SETTINGS))
        put("cast", Section(Settings.ACTION_CAST_SETTINGS))
        put("vpn", Section(Settings.ACTION_VPN_SETTINGS))
        put("device_info", Section(Settings.ACTION_DEVICE_INFO_SETTINGS))
        put("developer", Section(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        put("apps", Section(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
        put("default_apps", Section(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        put("notification_access", Section(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        put("dnd_access", Section(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        put("assistant", Section(Settings.ACTION_VOICE_INPUT_SETTINGS))
        put("assistant_gesture", Section("android.settings.ASSIST_GESTURE_SETTINGS"))
        put("app", Section(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, dataFor = { ctx ->
            Uri.parse("package:${ctx.packageName}")
        }))
        put("notifications", Section(Settings.ACTION_APP_NOTIFICATION_SETTINGS, extras = { ctx ->
            mapOf(Settings.EXTRA_APP_PACKAGE to ctx.packageName)
        }))
        // The exact grant path for halo_brightness, so the model never has to describe a
        // settings screen from memory.
        put("write_settings", Section(Settings.ACTION_MANAGE_WRITE_SETTINGS, dataFor = { ctx ->
            Uri.parse("package:${ctx.packageName}")
        }))
        put("overlay", Section(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        put("unknown_sources", Section(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, dataFor = { ctx ->
            Uri.parse("package:${ctx.packageName}")
        }))
        if (Build.VERSION.SDK_INT >= 31) {
            put("open_by_default", Section(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, dataFor = { ctx ->
                Uri.parse("package:${ctx.packageName}")
            }))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            put("app_language", Section(Settings.ACTION_APP_LOCALE_SETTINGS, dataFor = { ctx ->
                Uri.parse("package:${ctx.packageName}")
            }))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            put("exact_alarms", Section(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, dataFor = { ctx -> Uri.parse("package:${ctx.packageName}") }))
        }
    }

    /** Refresh available handlers so discovery follows installed apps and permission changes. */
    private val availableSections: Map<String, Section> get() = sections.filterValues { section -> resolveFor(section) != null }

    private fun resolveFor(section: Section): Intent? {
        val intent = intentFor(section) ?: return null
        return if (intent.resolveActivity(context.packageManager) != null) intent else null
    }

    private fun intentFor(section: Section): Intent? {
        val data = section.dataFor?.invoke(context)
        val intent = Intent(section.action)
        if (data != null) intent.data = data
        section.extras?.invoke(context)?.forEach { (key, value) -> intent.putExtra(key, value) }
        return intent
    }

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_open_settings",
            name = "halo_open_settings",
            description = "Open one Android settings screen. Only screens this device can actually open are listed; " +
                "opening a screen does not change any setting.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "section" to mapOf(
                        "type" to "string",
                        "enum" to availableSections.keys.sorted(),
                        "description" to "Settings screen to open",
                    ),
                ),
                "required" to emptyList<String>(),
                "additionalProperties" to false,
            ),
        ),
        ToolDefinition(
            id = "halo_share_text",
            name = "halo_share_text",
            description = "Open the Android share sheet; user chooses the recipient and confirms sending.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("text" to mapOf("type" to "string", "maxLength" to 2000)),
                "required" to listOf("text"),
                "additionalProperties" to false
            )
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: ToolRejectionKind) =
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)

        return when (toolCall.functionName) {
            "halo_open_settings" -> {
                val requested = toolCall.arguments["section"] as? String
                // A missing section is a legitimate request for general settings, but an
                // explicitly named unknown section must not silently open something else.
                val section = if (requested == null) {
                    availableSections["general"] ?: return unavailable(toolCall, "general")
                } else {
                    availableSections[requested]
                        ?: if (requested in sections) return unavailable(toolCall, requested)
                        else return reject(
                            "Unknown settings section; use one of: ${availableSections.keys.sorted().joinToString(", ")}",
                            ToolRejectionKind.INVALID_ARGUMENTS,
                        )
                }
                val intent = intentFor(section) ?: return unavailable(toolCall, requested ?: "general")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    context.startActivity(intent)
                    ToolExecutionResult.Success(
                        toolCallId = toolCall.id,
                        toolId = toolCall.functionName,
                        output = ToolResultEnvelope.success(
                            actionTruth = ActionTruth.DISPATCHED,
                            details = "section=${requested ?: "general"}\n" +
                                if (requested in setOf("internet", "wifi", "bluetooth", "nfc", "volume")) "Android requires confirmation in this panel. Please complete this step." else "note=The settings screen was opened; no setting was changed.",
                        ),
                        actionTruth = ActionTruth.DISPATCHED,
                    )
                } catch (_: ActivityNotFoundException) {
                    unavailable(toolCall, requested ?: "general")
                } catch (_: SecurityException) {
                    reject("Android denied opening this settings screen", ToolRejectionKind.PERMISSION_REQUIRED)
                }
            }

            "halo_share_text" -> {
                val text = toolCall.arguments["text"] as? String ?: return reject("Missing text", ToolRejectionKind.INVALID_ARGUMENTS)
                if (text.isBlank() || text.length > 2000) return reject("Text must contain 1..2000 characters", ToolRejectionKind.INVALID_ARGUMENTS)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                try {
                    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    return ToolExecutionResult.Failed(toolCall.id, toolCall.functionName, "Share sheet unavailable", failureKind = ToolFailureKind.UNAVAILABLE)
                } catch (_: SecurityException) {
                    return reject("Android denied sharing", ToolRejectionKind.PERMISSION_REQUIRED)
                }
                ToolExecutionResult.Success(
                    toolCallId = toolCall.id,
                    toolId = toolCall.functionName,
                    output = ToolResultEnvelope.success(
                        actionTruth = ActionTruth.DISPATCHED,
                        details = "note=The share sheet was opened. The user chooses whether anything is sent.",
                    ),
                    actionTruth = ActionTruth.DISPATCHED,
                )
            }

            else -> reject("Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)
        }
    }

    private fun unavailable(toolCall: ToolCall, section: String) = ToolExecutionResult.Failed(
        toolCallId = toolCall.id,
        toolId = toolCall.functionName,
        message = "This device has no activity for the \"$section\" settings screen",
        failureKind = ToolFailureKind.UNAVAILABLE,
        code = "SETTINGS_SECTION_UNAVAILABLE",
        hint = "Do not retry the same section. Tell the user that screen is not available on this phone.",
    )
}
