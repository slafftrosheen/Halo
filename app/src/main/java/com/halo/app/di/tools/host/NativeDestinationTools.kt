package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import com.halo.cognition.language.NativeDestination
import com.halo.app.nativeactions.NativeActivityRequests
import com.halo.app.nativeactions.NativeResultActivity
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

/** Fixed public contracts, not an arbitrary Intent/deep-link executor. */
class NativeDestinationTools(private val context: Context, private val isForeground: () -> Boolean = { true }) : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition("halo_native_destination", "halo_native_destination",
        "Open a fixed Android destination or receive selected content into Halo Inbox. Pickers require visible Halo and user selection; no automatic sharing or sending.",
        mapOf("type" to "object", "properties" to mapOf("destination" to mapOf("type" to "string",
            "enum" to NativeDestination.entries.map { it.wireId })), "required" to listOf("destination"), "additionalProperties" to false)),
        ToolDefinition("halo_native_result", "halo_native_result", "Read the result of a previously dispatched user-mediated picker request. Unknown after process death; never repeats the action.",
            mapOf("type" to "object", "properties" to mapOf("requestId" to mapOf("type" to "string")), "required" to listOf("requestId"), "additionalProperties" to false)))

    internal fun intentFor(destination: NativeDestination): Intent? = when (destination) {
        NativeDestination.CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        NativeDestination.VIDEO_CAMERA -> Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
        NativeDestination.GALLERY -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)
        NativeDestination.BROWSER -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        NativeDestination.MUSIC -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        NativeDestination.REMINDERS -> context.packageManager.getLaunchIntentForPackage("com.google.android.apps.tasks")
        NativeDestination.CALL_LOG -> Intent(Intent.ACTION_VIEW).setDataAndType(
            android.provider.CallLog.Calls.CONTENT_URI, android.provider.CallLog.Calls.CONTENT_TYPE)
        NativeDestination.CONTACT_PICKER -> Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        NativeDestination.DOCUMENT_PICKER, NativeDestination.DOCUMENTS_PICKER -> Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, destination == NativeDestination.DOCUMENTS_PICKER)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        NativeDestination.CONTENT_PICKER -> Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        NativeDestination.FOLDER_PICKER -> Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        NativeDestination.PHOTO_PICKER -> if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
            else Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        // Full-size photo capture uses Halo's existing owned-output capture UI; no thumbnail-only result is claimed as a saved photo.
        NativeDestination.PHOTO_CAPTURE -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        NativeDestination.VIDEO_CAPTURE -> Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        NativeDestination.SOUND_RECORDING -> Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        NativeDestination.BLUETOOTH_ENABLE -> Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
        NativeDestination.HAPTIC -> null
        NativeDestination.DOCUMENT_SCAN -> Intent(context, com.halo.app.nativeactions.NativeDocumentScanActivity::class.java)
        NativeDestination.WIFI_ADD_NETWORK -> if (Build.VERSION.SDK_INT >= 30) Intent(context, com.halo.app.nativeactions.NativeWifiNetworkActivity::class.java) else null
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(message: String, kind: ToolRejectionKind) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, message, kind)
        if (toolCall.functionName == "halo_native_result") {
            val id = toolCall.arguments["requestId"] as? String ?: return reject("requestId required", ToolRejectionKind.INVALID_ARGUMENTS)
            val receipt = NativeActivityRequests.receipt(id)
            val truth = if (receipt?.completed == true) ActionTruth.CONFIRMED else ActionTruth.UNKNOWN
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                ToolResultEnvelope.success(truth, details = receipt?.details ?: if (NativeActivityRequests.active(id)) "Waiting for user result" else "Result unknown or expired; do not repeat automatically"), truth)
        }
        if (toolCall.functionName != "halo_native_destination") return reject("Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)
        if (toolCall.arguments.keys != setOf("destination")) return reject("Only destination is accepted", ToolRejectionKind.INVALID_ARGUMENTS)
        val destination = (toolCall.arguments["destination"] as? String)?.let(NativeDestination::fromWire)
            ?: return reject("Unknown destination", ToolRejectionKind.INVALID_ARGUMENTS)
        if (!isForeground()) return reject("Open Halo before starting this user-mediated action", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        if (destination == NativeDestination.HAPTIC) {
            val audio = context.getSystemService(android.media.AudioManager::class.java)
            if (audio.ringerMode == android.media.AudioManager.RINGER_MODE_SILENT)
                return reject("Haptic ping is disabled while the phone is silent", ToolRejectionKind.UNSUPPORTED_OPERATION)
            val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
                else context.getSystemService(android.os.Vibrator::class.java)
            if (!vibrator.hasVibrator()) return reject("No vibrator present", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
            return try {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(60, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                    ToolResultEnvelope.success(ActionTruth.DISPATCHED, details = "One 60 ms haptic requested; physical vibration not verified"), ActionTruth.DISPATCHED)
            } catch (_: SecurityException) { reject("Vibration permission required", ToolRejectionKind.PERMISSION_REQUIRED) }
        }
        val target = intentFor(destination) ?: return reject(
            if (destination == NativeDestination.REMINDERS) "Google Tasks is not installed or has no available launcher" else "This destination is not supported on this Android version",
            if (destination == NativeDestination.REMINDERS) ToolRejectionKind.CAPABILITY_UNAVAILABLE else ToolRejectionKind.UNSUPPORTED_OPERATION)
        val resultOwned = destination in setOf(NativeDestination.CONTACT_PICKER, NativeDestination.DOCUMENT_PICKER,
            NativeDestination.DOCUMENTS_PICKER, NativeDestination.CONTENT_PICKER, NativeDestination.FOLDER_PICKER,
            NativeDestination.PHOTO_PICKER, NativeDestination.PHOTO_CAPTURE, NativeDestination.VIDEO_CAPTURE, NativeDestination.SOUND_RECORDING)
        return try {
            if (resultOwned || destination in setOf(NativeDestination.DOCUMENT_SCAN, NativeDestination.WIFI_ADD_NETWORK)) {
                val (id, _) = NativeActivityRequests.create()
                try {
                    val activity = when (destination) {
                        NativeDestination.DOCUMENT_SCAN -> com.halo.app.nativeactions.NativeDocumentScanActivity::class.java
                        NativeDestination.WIFI_ADD_NETWORK -> com.halo.app.nativeactions.NativeWifiNetworkActivity::class.java
                        else -> NativeResultActivity::class.java
                    }
                    context.startActivity(Intent(context, activity)
                        .putExtra("requestId", id).putExtra("destination", destination.wireId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                        ToolResultEnvelope.success(ActionTruth.DISPATCHED, details = "requestId=$id; Android picker requested; use halo_native_result after the user returns"), ActionTruth.DISPATCHED)
                } catch (error: Exception) { NativeActivityRequests.cancel(id); throw error }
            } else {
                context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                    ToolResultEnvelope.success(ActionTruth.DISPATCHED, details = "destination=${destination.wireId}; system action requested, final state not verified"), ActionTruth.DISPATCHED)
            }
        } catch (_: android.content.ActivityNotFoundException) { reject("No available handler", ToolRejectionKind.CAPABILITY_UNAVAILABLE) }
        catch (_: SecurityException) { reject("Android permission or role required", ToolRejectionKind.PERMISSION_REQUIRED) }
        catch (_: IllegalStateException) { reject("Too many pending native requests; complete or cancel an earlier request", ToolRejectionKind.RESOURCE_UNAVAILABLE) }
    }
}
