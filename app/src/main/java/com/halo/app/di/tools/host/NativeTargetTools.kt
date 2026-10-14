package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*

/** Exact platform-owned targets, with typed slots instead of caller supplied URIs/components. */
class NativeTargetTools(private val context: Context, private val isForeground: () -> Boolean = { true }) : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition("halo_native_target", "halo_native_target",
        "Open an exact installed app information/uninstall screen, selected contact, or permitted calendar event. User reviews edits/uninstall; receipt is dispatch only.",
        mapOf("type" to "object", "properties" to mapOf(
            "action" to mapOf("type" to "string", "enum" to listOf("app_info", "app_notifications", "uninstall", "contact_view", "contact_edit", "contact_share", "contact_from_number", "call", "calendar_view", "calendar_edit", "calendar_date")),
            "appName" to mapOf("type" to "string", "maxLength" to 150),
            "packageName" to mapOf("type" to "string", "maxLength" to 200),
            "targetHandle" to mapOf("type" to "string", "maxLength" to 64),
            "phoneNumber" to mapOf("type" to "string", "maxLength" to 40),
            "eventId" to mapOf("type" to "integer", "minimum" to 1),
            "timeMs" to mapOf("type" to "integer", "minimum" to 1)), "required" to listOf("action"), "additionalProperties" to false)))

    internal fun intentFor(arguments: Map<String, Any>): Intent {
        val action = arguments["action"] as? String ?: error("action required")
        return when (action) {
            "app_info", "app_notifications", "uninstall" -> {
                require(arguments.keys in setOf(setOf("action", "packageName"), setOf("action", "appName")))
                val name = (arguments["packageName"] as? String) ?: run {
                    val label = arguments["appName"] as? String ?: error("App name required")
                    require(label.isNotBlank() && label.length <= 150)
                    val matches = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                        .filter { it.loadLabel(context.packageManager).toString().equals(label.trim(), true) }
                        .map { it.activityInfo.packageName }.distinct()
                    require(matches.size == 1) { "No unique visible app matches; choose its exact package from the app list" }
                    matches.single()
                }
                require(name.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")) && name.length <= 200)
                val info = context.packageManager.getApplicationInfo(name, 0)
                if (action == "uninstall") {
                    require(name != context.packageName && info.flags and
                        (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) { "Protected or system app cannot be uninstalled by Halo" }
                    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", name, null))
                } else if (action == "app_notifications") Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, name)
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", name, null))
            }
            "contact_view", "contact_edit", "contact_share" -> {
                require(arguments.keys == setOf("action", "targetHandle"))
                val handle = arguments["targetHandle"] as? String ?: error("Select a contact first")
                require(handle.length <= 64)
                val target = context.getSharedPreferences("native_selected_targets", Context.MODE_PRIVATE).getString(handle, null)
                    ?.let(Uri::parse) ?: error("Select a contact first")
                require(target.scheme == "content" && target.authority == ContactsContract.AUTHORITY)
                var lookup: String? = null
                context.contentResolver.query(target, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY), null, null, null).use {
                    require(it?.moveToFirst() == true) { "Contact expired or unavailable" }
                    lookup = requireNotNull(it).getString(1)
                }
                if (action == "contact_share") {
                    val vcard = ContactsContract.Contacts.CONTENT_VCARD_URI.buildUpon().appendPath(requireNotNull(lookup)).build()
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType(ContactsContract.Contacts.CONTENT_VCARD_TYPE)
                        .putExtra(Intent.EXTRA_STREAM, vcard).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {
                            clipData = android.content.ClipData.newRawUri("Selected contact card", vcard)
                        }, null)
                } else Intent(if (action == "contact_edit") Intent.ACTION_EDIT else Intent.ACTION_VIEW, target)
            }
            "contact_from_number" -> {
                require(arguments.keys == setOf("action", "phoneNumber"))
                val supplied = arguments["phoneNumber"] as? String ?: error("phoneNumber required")
                require(supplied.length <= 40 && supplied.matches(Regex("[+0-9 ()-]+")))
                val number = CommunicationTools.canonicalDialNumber(supplied)
                require(number.matches(Regex("\\+?[0-9]{3,20}")))
                Intent(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT, Uri.fromParts("tel", number, null))
            }
            "calendar_view", "calendar_edit" -> {
                require(arguments.keys == setOf("action", "eventId"))
                val raw = arguments["eventId"] as? Number ?: error("eventId required")
                val id = raw.toLong(); require(id > 0 && raw.toDouble() == id.toDouble())
                val uri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                context.contentResolver.query(uri, arrayOf(CalendarContract.Events._ID), null, null, null).use {
                    require(it?.moveToFirst() == true) { "Event not accessible" }
                }
                Intent(if (action == "calendar_edit") Intent.ACTION_EDIT else Intent.ACTION_VIEW, uri)
            }
            "calendar_date" -> {
                require(arguments.keys == setOf("action", "timeMs"))
                val raw = arguments["timeMs"] as? Number ?: error("timeMs required")
                val time = raw.toLong(); require(time > 0 && raw.toDouble() == time.toDouble())
                Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(time.toString()).build())
            }
            else -> error("Unknown target action")
        }
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(message: String, kind: ToolRejectionKind) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, message, kind)
        if (toolCall.functionName != "halo_native_target") return reject("Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)
        if (!isForeground()) return reject("Open Halo before reviewing this action", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        if (toolCall.arguments["action"] == "call") {
            val number = toolCall.arguments["phoneNumber"] as? String
            if (toolCall.arguments.keys != setOf("action", "phoneNumber") || number == null || !number.matches(Regex("\\+?[0-9]{3,20}")))
                return reject("An exact dialable number is required", ToolRejectionKind.INVALID_ARGUMENTS)
            val (requestId, _) = try { com.halo.app.nativeactions.NativeActivityRequests.create() } catch (_: IllegalStateException) {
                return reject("Complete or cancel an earlier native request first", ToolRejectionKind.RESOURCE_UNAVAILABLE)
            }
            return try {
                context.startActivity(Intent(context, com.halo.app.nativeactions.NativeDirectCallActivity::class.java)
                    .putExtra("requestId", requestId).putExtra("phoneNumber", number).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                    ToolResultEnvelope.success(ActionTruth.DISPATCHED, details = "Call review requested; requestId=$requestId. No call placed by this dispatch."), ActionTruth.DISPATCHED)
            } catch (_: SecurityException) { com.halo.app.nativeactions.NativeActivityRequests.cancel(requestId); return reject("Android denied call review", ToolRejectionKind.PERMISSION_REQUIRED) }
        }
        return try {
            context.startActivity(intentFor(toolCall.arguments).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                ToolResultEnvelope.success(ActionTruth.DISPATCHED, details = "${toolCall.arguments["action"]} requested; saving, uninstalling, or destination effect not verified"), ActionTruth.DISPATCHED)
        } catch (_: SecurityException) { reject("Android permission or target grant required", ToolRejectionKind.PERMISSION_REQUIRED) }
        catch (_: android.content.pm.PackageManager.NameNotFoundException) { reject("Package not visible or installed", ToolRejectionKind.RESOURCE_UNAVAILABLE) }
        catch (_: android.content.ActivityNotFoundException) { reject("Handler unavailable", ToolRejectionKind.CAPABILITY_UNAVAILABLE) }
        catch (error: IllegalArgumentException) { reject(error.message ?: "Invalid target", ToolRejectionKind.INVALID_ARGUMENTS) }
        catch (error: IllegalStateException) { reject(error.message ?: "Unavailable target", ToolRejectionKind.RESOURCE_UNAVAILABLE) }
    }
}
