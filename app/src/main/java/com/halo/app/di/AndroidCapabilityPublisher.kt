package com.halo.app.di

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.halo.runtime.cognition.tools.Capability
import com.halo.runtime.cognition.tools.CapabilityAvailability
import com.halo.runtime.cognition.tools.CapabilityCatalogue
import com.halo.runtime.cognition.tools.CapabilityId
import com.halo.runtime.cognition.tools.CapabilityPublisher
import com.halo.runtime.cognition.tools.ToolCategory

/**
 * RUNTIME-017 host adapter: advertises what THIS Android host can do as semantic
 * Runtime capabilities. Capabilities are generic (launch applications, open web,
 * read notifications); the fact that they are fulfilled via Android intents lives
 * only in this adapter.
 */
class AndroidCapabilityPublisher(
    private val context: Context,
) : CapabilityPublisher {

    override val scope: String = "android-host"

    override fun publishInto(catalogue: CapabilityCatalogue) {
        val pm = context.packageManager

        val canLaunchApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).isNotEmpty()
        val canOpenWeb = intentResolves(pm, Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
        val canWebSearch = intentResolves(pm, Intent(Intent.ACTION_WEB_SEARCH))
        val canNavigate = intentResolves(pm, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=place")))
        val canDial = intentResolves(pm, Intent(Intent.ACTION_DIAL))
        val canSetAlarm = intentResolves(pm, Intent(android.provider.AlarmClock.ACTION_SET_ALARM))

        listOf(
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
                granted(context, android.Manifest.permission.READ_CONTACTS)),
            capability("host.alarm.set", "Set alarms",
                "Create an alarm in the host alarm application.", ToolCategory.AUTOMATION, canSetAlarm),
            capability("host.notification.read", "Read notifications",
                "Read posted notifications for digests and summaries.", ToolCategory.INFORMATION,
                isListenerEnabled(context)),
            capability("host.volume.control", "Control volume",
                "Adjust the host media volume.", ToolCategory.DEVICE_CONTROL, true),
            capability("host.flashlight.control", "Control flashlight",
                "Turn the host camera flash on or off.", ToolCategory.DEVICE_CONTROL,
                granted(context, android.Manifest.permission.CAMERA) &&
                    pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)),
            capability("host.audio.output", "Audio output",
                "Play synthesized speech through host audio routes.", ToolCategory.MEDIA, true),
            capability("workspace.read", "Read workspace",
                "Read user-curated notes and documents in the Halo workspace.", ToolCategory.WORKSPACE, true),
            capability("workspace.write", "Write workspace",
                "Create and organize user-curated notes and documents.", ToolCategory.WORKSPACE, true),
        ).forEach { catalogue.publish(it) }
    }

    private fun capability(
        id: String,
        name: String,
        description: String,
        category: ToolCategory,
        available: Boolean,
    ) = Capability(
        id = CapabilityId(id),
        displayName = name,
        description = description,
        category = category,
        availability = if (available) CapabilityAvailability.AVAILABLE else CapabilityAvailability.UNAVAILABLE,
        source = scope,
    )

    private fun intentResolves(pm: PackageManager, intent: Intent): Boolean =
        intent.resolveActivity(pm) != null

    private fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun isListenerEnabled(context: Context): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
}
