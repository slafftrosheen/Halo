package com.halo.app.di

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import androidx.core.app.NotificationManagerCompat
import com.halo.host.android.accessibility.HaloAccessibilityService
import com.halo.host.android.assistant.AssistantEligibilityInspector
import com.halo.runtime.cognition.tools.*

/**
 * RUNTIME-017 / RUNTIME-020.1 host adapter: advertises what THIS Android host can do as semantic
 * Runtime capabilities. Capabilities are generic (launch applications, open web,
 * read notifications); the fact that they are fulfilled via Android intents lives
 * only in this adapter.
 */
class AndroidCapabilityPublisher(
    private val context: Context,
) : CapabilityPublisher {

    override val scope: String = "host:primary"

    override fun publishInto(catalogue: CapabilityCatalogue) {
        refreshInto(catalogue)
    }

    fun snapshotCapabilities(): List<CapabilityInstance> {
        val pm = context.packageManager

        val canLaunchApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).isNotEmpty()
        val canOpenWeb = intentResolves(pm, Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
        val canWebSearch = intentResolves(pm, Intent(Intent.ACTION_WEB_SEARCH))
        val canNavigate = intentResolves(pm, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=place")))
        val canDial = intentResolves(pm, Intent(Intent.ACTION_DIAL))
        val canSetAlarm = intentResolves(pm, Intent(AlarmClock.ACTION_SET_ALARM))
        // RUNTIME-056 native abilities.
        val canSetTimer = intentResolves(pm, Intent(AlarmClock.ACTION_SET_TIMER))
        val canOpenSettings = intentResolves(pm, Intent(android.provider.Settings.ACTION_SETTINGS))
        val canControlRinger = context.getSystemService(Context.AUDIO_SERVICE) != null
        val canWriteSystemSettings = android.provider.Settings.System.canWrite(context)
        val assistant = AssistantEligibilityInspector(context).inspect()

        return listOf(
            capability("host.calendar.read", "Read calendar", "Read upcoming calendar events.", ToolCategory.INFORMATION, granted(context, Manifest.permission.READ_CALENDAR)),
            capability("host.calendar.write", "Write calendar", "Create an approved calendar event.", ToolCategory.AUTOMATION, granted(context, Manifest.permission.WRITE_CALENDAR)),
            capability("host.application.launch", "Launch applications",
                "Open an installed application on the host.", ToolCategory.HOST_CONTROL, canLaunchApps),
            capability("host.application.enumerate", "Enumerate applications",
                "List launchable installed applications.", ToolCategory.SYSTEM, canLaunchApps),
            capability("host.web.open", "Open web pages",
                "Open an HTTP or HTTPS URL in the host browser.", ToolCategory.NAVIGATION, canOpenWeb),
            capability("host.web.search", "Web search",
                "Run a web search through the host search surface.", ToolCategory.INFORMATION, canWebSearch),
            capability("host.navigation.open", "Open navigation",
                "Open a route or destination in a maps application.", ToolCategory.NAVIGATION, canNavigate),
            capability("host.dialer.open", "Open dialer",
                "Open the dialer with a phone number prepared.", ToolCategory.COMMUNICATION, canDial),
            capability("host.contacts.read", "Read contacts",
                "Resolve contact names to phone numbers or emails.", ToolCategory.COMMUNICATION,
                granted(context, Manifest.permission.READ_CONTACTS)),
            capability("host.alarm.set", "Set alarms",
                "Create an alarm in the host alarm application.", ToolCategory.AUTOMATION, canSetAlarm),
            capability("host.notification.live", "Live notifications", "Connected notification listener.", ToolCategory.INFORMATION,
                isListenerEnabled(context) && com.halo.app.notification.LiveNotificationOwner.connected()),
            capability("host.notification.read", "Read notifications",
                "Read posted notifications for digests and summaries.", ToolCategory.INFORMATION,
                isListenerEnabled(context)),
            capability("host.volume.control", "Control volume",
                "Adjust the host media volume.", ToolCategory.DEVICE_CONTROL, true),
            capability("host.flashlight.control", "Control flashlight",
                "Turn the host camera flash on or off.", ToolCategory.DEVICE_CONTROL,
                granted(context, Manifest.permission.CAMERA) &&
                    pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)),
            capability("host.audio.input", "Built-in microphone",
                "Capture audio through the host microphone.", ToolCategory.PERCEPTION,
                granted(context, Manifest.permission.RECORD_AUDIO)),
            capability("host.audio.output", "Built-in speaker",
                "Play audio through the host speaker or wired headset.", ToolCategory.MEDIA, true),
            capability("host.camera.still", "Built-in camera",
                "Host still capture is not exposed until a working CameraX StillImageSource is wired.",
                ToolCategory.PERCEPTION,
                false),
            capability("host.screen.observe", "Read active screen",
                "Read a bounded, redacted semantic accessibility snapshot of the active Android screen.",
                ToolCategory.PERCEPTION, HaloAccessibilityService.isEnabled),
            capability("host.screen.control", "Control active screen",
                "Perform confirmed accessibility actions on the active Android screen.",
                ToolCategory.HOST_CONTROL, HaloAccessibilityService.isEnabled),
            capability("host.assistant.native", "Android default assistant",
                "Receive Android assist gestures and voice-interaction sessions as the selected system assistant.",
                ToolCategory.HOST_CONTROL, assistant.activeVoiceInteractionService),
            capability("host.device.inspect", "Describe this device",
                "Report the phone's model, Android version, screen, memory and locale.",
                ToolCategory.SYSTEM, true),
            capability("host.battery.read", "Read battery state",
                "Report battery level, charging state and power-save state.",
                ToolCategory.SYSTEM, context.getSystemService(Context.BATTERY_SERVICE) != null),
            capability("host.network.read", "Read network state",
                "Report the active connection type, metering and local addresses.",
                ToolCategory.SYSTEM, granted(context, Manifest.permission.ACCESS_NETWORK_STATE)),
            capability("host.clipboard.read", "Read the clipboard",
                "Read text the user last copied. Android only allows this while Halo is the focused app.",
                ToolCategory.INFORMATION, true),
            capability("host.clipboard.write", "Write the clipboard",
                "Copy text so the user can paste it in another app.",
                ToolCategory.DEVICE_CONTROL, true),
            capability("host.web.fetch", "Read web pages",
                "Read the readable text of a public web page on the user's behalf.",
                ToolCategory.INFORMATION, granted(context, Manifest.permission.INTERNET)),
            capability("host.timer.set", "Set timers",
                "Start a countdown timer in the host clock app.",
                ToolCategory.AUTOMATION, canSetTimer),
            capability("host.ringer.control", "Control sound mode",
                "Switch the phone between ring, vibrate and silent.",
                ToolCategory.DEVICE_CONTROL, canControlRinger),
            capability("host.screen.brightness", "Control screen brightness",
                "Set screen brightness. Needs the user to allow Halo to modify system settings.",
                ToolCategory.DEVICE_CONTROL, canWriteSystemSettings),
            capability("host.settings.open", "Open Android settings",
                "Open an Android settings screen that this device can actually display.",
                ToolCategory.HOST_CONTROL, canOpenSettings),
            capability("workspace.read", "Read workspace",
                "Read user-curated notes and documents in the Halo workspace.", ToolCategory.WORKSPACE, true),
            capability("workspace.write", "Write workspace",
                "Create and organize user-curated notes and documents.", ToolCategory.WORKSPACE, true),
        )
    }

    fun refreshInto(catalogue: CapabilityCatalogue) {
        val next = snapshotCapabilities()
        val current = catalogue.snapshot.value.capabilities.filter { it.ownerId == scope && it.sourceKind == CapabilitySourceKind.HOST }
        if (current.toSet() != next.toSet()) catalogue.replaceScope(scope, next)
    }

    private fun capability(
        id: String,
        name: String,
        description: String,
        category: ToolCategory,
        available: Boolean,
    ) = CapabilityInstance(
        id = CapabilityId(id),
        displayName = name,
        description = description,
        category = category,
        sourceKind = CapabilitySourceKind.HOST,
        ownerId = "host:primary",
        endpointId = "host:primary",
        availability = if (available) CapabilityAvailability.AVAILABLE else CapabilityAvailability.NEED_SETUP,
    )

    private fun intentResolves(pm: PackageManager, intent: Intent): Boolean =
        intent.resolveActivity(pm) != null

    private fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun isListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
}
