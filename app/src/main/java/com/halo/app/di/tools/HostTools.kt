package com.halo.app.di.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import com.halo.host.android.reminder.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HostTools(private val context: Context, private val isForeground: () -> Boolean = { true }) : ToolExecutor {
    private val sections = mapOf(
        "general" to Settings.ACTION_SETTINGS, "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS, "sound" to Settings.ACTION_SOUND_SETTINGS,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS, "display" to Settings.ACTION_DISPLAY_SETTINGS,
        "app" to Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "notifications" to Settings.ACTION_APP_NOTIFICATION_SETTINGS,
        "exact_alarms" to Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
    )
    private fun string(max: Int = 500) = mapOf("type" to "string", "minLength" to 1, "maxLength" to max)
    private fun definition(id: String, description: String, properties: Map<String, Any>, required: List<String>) =
        ToolDefinition(id, id, description, mapOf("type" to "object", "properties" to properties,
            "required" to required, "additionalProperties" to false))

    override fun definitions() = listOf(
        definition("halo_open_settings", "Open a specific Android settings page.",
            mapOf("section" to (string() + ("enum" to sections.keys.toList()))), emptyList()),
        definition("halo_prepare_contact", "Open a contact draft for the user to save; does not save a contact.",
            mapOf("name" to string(), "phone" to string(), "email" to string()), listOf("name")),
        definition("halo_share_text", "Open the Android share sheet; user chooses the recipient and confirms sending.",
            mapOf("text" to string(2000)), listOf("text")),
        definition("halo_create_reminder", "Persist and schedule a reminder. Use an ISO-8601 timestamp with UTC offset. Flexible delivery is the default; exact delivery needs Android exact-alarm access.",
            mapOf("message" to string(1000), "at" to string(100), "precision" to (string() + ("enum" to listOf("FLEXIBLE", "EXACT")))), listOf("message", "at")),
        definition("halo_list_reminders", "List saved reminder IDs, times and actual scheduling status.", emptyMap(), emptyList())
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult = withContext(Dispatchers.IO) {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)
        fun success(output: String) = ToolExecutionResult.Success(toolCall.id, toolCall.functionName, output)
        val definition = definitions().firstOrNull { it.id == toolCall.functionName } ?: return@withContext reject("Unknown host action")
        val properties = definition.parameters["properties"] as Map<*, *>
        val required = definition.parameters["required"] as List<*>
        if (toolCall.arguments.keys.any { it !in properties } || required.any { it !is String || !toolCall.arguments.containsKey(it) })
            return@withContext reject("Missing or unexpected arguments")
        for ((key, value) in toolCall.arguments) {
            val schema = properties[key] as Map<*, *>
            if (value !is String || value.isBlank() || value.length > (schema["maxLength"] as Int)) return@withContext reject("Invalid $key")
            val allowed = schema["enum"] as? List<*>
            if (allowed != null && value !in allowed) return@withContext reject("Unsupported $key")
        }
        fun arg(key: String) = (toolCall.arguments[key] as? String)?.trim().orEmpty()
        val store = ReminderRecordStore(context)
        when (toolCall.functionName) {
            "halo_create_reminder" -> {
                val at = runCatching { Instant.parse(arg("at")) }.getOrNull()
                    ?: return@withContext reject("Use an ISO-8601 date/time with offset, for example 2026-10-01T09:00:00+03:00")
                if (!at.isAfter(Instant.now())) return@withContext reject("Reminder time must be in the future")
                val id = "tool:${toolCall.id}"
                val record = ReminderRecord(id, arg("message"), at.toEpochMilli(), arg("precision").ifBlank { "FLEXIBLE" })
                val existing = store.all().firstOrNull { it.id == id }
                if (existing != null && existing != record) return@withContext reject("This action ID already belongs to another reminder")
                store.save(record)
                store.reconcile()
                return@withContext when (store.status(id)) {
                    ReminderStatus.Scheduled -> success("Scheduled reminder [$id] at $at (${record.precision}).")
                    ReminderStatus.Blocked -> ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
                        "Saved reminder [$id], but it is NOT scheduled. Grant exact-alarm access in Settings to schedule it.")
                    else -> ToolExecutionResult.Failed(toolCall.id, toolCall.functionName, "Reminder [$id] saved but scheduling failed: ${store.status(id)}")
                }
            }
            "halo_list_reminders" -> return@withContext success(store.all().sortedBy { it.triggerAtEpochMs }.take(20)
                .joinToString("\n") { "[${it.id}] ${Instant.ofEpochMilli(it.triggerAtEpochMs)} ${store.status(it.id)}: ${it.message}" }
                .take(4000).ifEmpty { "No saved reminders" })
        }
        if (!isForeground()) return@withContext reject("Open Halo to launch this Android screen")
        val intent = when (toolCall.functionName) {
            "halo_open_settings" -> {
                val section = arg("section").ifBlank { "general" }
                Intent(sections.getValue(section)).apply {
                    if (section in setOf("app", "exact_alarms")) data = Uri.parse("package:${context.packageName}")
                    if (section == "notifications") putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            }
            "halo_prepare_contact" -> Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                putExtra(ContactsContract.Intents.Insert.NAME, arg("name"))
                if (arg("phone").isNotEmpty()) putExtra(ContactsContract.Intents.Insert.PHONE, arg("phone"))
                if (arg("email").isNotEmpty()) putExtra(ContactsContract.Intents.Insert.EMAIL, arg("email"))
            }
            "halo_share_text" -> Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, arg("text"))
            }, "Share from Halo")
            else -> return@withContext reject("Unsupported host action")
        }
        try {
            withContext(Dispatchers.Main) { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                "Dispatched Android screen. User completion has not been observed.", ActionTruth.ACKNOWLEDGED)
        } catch (error: android.content.ActivityNotFoundException) {
            reject("No installed app can handle this action")
        } catch (error: SecurityException) {
            reject("Android denied this action: ${error.message}")
        }
    }
}
