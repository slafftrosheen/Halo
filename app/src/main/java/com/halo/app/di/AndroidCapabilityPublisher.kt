package com.halo.app.di

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import androidx.core.app.NotificationManagerCompat
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

        return listOf(
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
                "Capture still images using host camera.", ToolCategory.PERCEPTION,
                granted(context, Manifest.permission.CAMERA)),
            capability("workspace.read", "Read workspace",
                "Read user-curated notes and documents in the Halo workspace.", ToolCategory.WORKSPACE, true),
            capability("workspace.write", "Write workspace",
                "Create and organize user-curated notes and documents.", ToolCategory.WORKSPACE, true),
        )
    }

    fun refreshInto(catalogue: CapabilityCatalogue) {
        catalogue.replaceScope(scope, snapshotCapabilities())
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
