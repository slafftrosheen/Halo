package com.halo.app.ui.screens.devices

import androidx.annotation.StringRes
import com.halo.app.R
import com.halo.app.ui.icons.HaloGlyph

/**
 * Seven human-facing phone abilities in BrandBook v1.0, p.42.
 *
 * Device capability publication is NOT a permission grant. The catalogue currently
 * exposes available capabilities, but not the effective ask-first/denied policy.
 * For this reason the UI displays "Available" / "Not reported" rather than
 * manufacturing the three-state permission semantics in the artwork.
 *
 * Capability names are the stable, user-readable labels returned by
 * RuntimeUiFormatters.formatCapability. Unrecognized capabilities remain
 * discoverable in the technical details view.
 */
internal data class PhoneCapabilityGroup(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val icon: HaloGlyph,
    val names: Set<String>,
)

internal val phoneCapabilityGroups: List<PhoneCapabilityGroup> = listOf(
    PhoneCapabilityGroup(
        R.string.brand_phone_hear, R.string.brand_phone_hear_summary, HaloGlyph.LISTEN,
        setOf("Microphone", "Speaker", "Control volume", "Audio input", "Audio output")
    ),
    PhoneCapabilityGroup(
        R.string.brand_phone_screen, R.string.brand_phone_screen_summary, HaloGlyph.PHONE,
        setOf("Screen observe", "Screen control", "Inspect", "Screen capture")
    ),
    PhoneCapabilityGroup(
        R.string.brand_phone_controls, R.string.brand_phone_controls_summary, HaloGlyph.PHONE,
        setOf("Set alarms", "Timer set", "Control flashlight", "Screen brightness",
            "Ringer control", "Settings open", "Battery read", "Network read",
            "Open dialer", "Assistant native")
    ),
    PhoneCapabilityGroup(
        R.string.brand_phone_apps, R.string.brand_phone_apps_summary, HaloGlyph.CLOUD,
        setOf("List applications", "Open applications", "Native destination",
            "Open web pages", "Open navigation", "Web search", "Web fetch")
    ),
    PhoneCapabilityGroup(
        R.string.brand_phone_calendar, R.string.brand_phone_calendar_summary, HaloGlyph.ACTIVITY,
        setOf("Calendar read", "Calendar write", "Read contacts")
    ),
    PhoneCapabilityGroup(
        R.string.brand_phone_notifications, R.string.brand_phone_notifications_summary, HaloGlyph.ACTIVITY,
        setOf("Read notifications", "Notification live")
    ),
    PhoneCapabilityGroup(
        R.string.brand_phone_workspace, R.string.brand_phone_workspace_summary, HaloGlyph.NOTE,
        setOf("Clipboard read", "Clipboard write", "Read workspace", "Write workspace")
    ),
)

internal data class PhoneCapabilitySummary(
    val group: PhoneCapabilityGroup,
    val available: List<String>,
) {
    val hasAvailableCapability: Boolean get() = available.isNotEmpty()
}

/** Pure projection. Never infer permission or execution rights from this list. */
internal fun groupPhoneCapabilities(reported: List<String>): List<PhoneCapabilitySummary> {
    val present = reported.toSet()
    return phoneCapabilityGroups.map { group ->
        PhoneCapabilitySummary(group, group.names.filter { it in present }.sorted())
    }
}

internal fun ungroupedPhoneCapabilities(reported: List<String>): List<String> {
    val known = phoneCapabilityGroups.flatMap { it.names }.toSet()
    return reported.filterNot { it in known }.distinct().sorted()
}
