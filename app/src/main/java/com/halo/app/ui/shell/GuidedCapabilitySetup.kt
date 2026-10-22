package com.halo.app.ui.shell

/** One user-explained Android runtime capability request in the resumable setup pass. */
internal data class GuidedCapabilityRequest(
    val id: String,
    val permissions: Set<String> = emptySet(),
    val special: Boolean = false,
)

internal fun guidedCapabilityInventory(sdk: Int): List<GuidedCapabilityRequest> = buildList {
    add(GuidedCapabilityRequest("microphone", setOf("android.permission.RECORD_AUDIO")))
    if (sdk >= 33) add(GuidedCapabilityRequest("notifications", setOf("android.permission.POST_NOTIFICATIONS")))
    add(GuidedCapabilityRequest("contacts", setOf("android.permission.READ_CONTACTS")))
    add(GuidedCapabilityRequest("calendar_read", setOf("android.permission.READ_CALENDAR")))
    add(GuidedCapabilityRequest("calendar_write", setOf("android.permission.WRITE_CALENDAR")))
    add(GuidedCapabilityRequest("camera", setOf("android.permission.CAMERA")))
    // Android 12+ requires requesting coarse and fine together; approximate access remains valid.
    add(GuidedCapabilityRequest("location", if (sdk >= 31) setOf(
        "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION",
    ) else setOf("android.permission.ACCESS_FINE_LOCATION")))
    add(GuidedCapabilityRequest("bluetooth", if (sdk >= 31) setOf(
        "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT",
    ) else setOf("android.permission.ACCESS_FINE_LOCATION")))
    if (sdk >= 33) add(GuidedCapabilityRequest("nearby_wifi", setOf("android.permission.NEARBY_WIFI_DEVICES")))
    if (sdk >= 37) add(GuidedCapabilityRequest("local_network", setOf("android.permission.ACCESS_LOCAL_NETWORK")))
    listOf("notification_access", "accessibility", "write_settings", "notification_policy", "overlay", "assistant")
        .forEach { add(GuidedCapabilityRequest(it, special = true)) }
    if (sdk >= 31) add(GuidedCapabilityRequest("exact_alarm", special = true))
}

internal enum class CapabilityAccessState { ALLOWED, LIMITED, NOT_ASKED, DECLINED, SETTINGS_REQUIRED, UNAVAILABLE }

internal fun capabilityAccessState(available: Boolean, granted: Boolean, special: Boolean,
    attempted: Boolean, rationale: Boolean): CapabilityAccessState = when {
    !available -> CapabilityAccessState.UNAVAILABLE
    granted -> CapabilityAccessState.ALLOWED
    special -> CapabilityAccessState.SETTINGS_REQUIRED
    rationale -> CapabilityAccessState.DECLINED
    attempted -> CapabilityAccessState.SETTINGS_REQUIRED
    else -> CapabilityAccessState.NOT_ASKED
}

/** Selection never re-asks an attempted item, including a request interrupted by process death. */
internal fun nextGuidedCapability(inventory: List<GuidedCapabilityRequest>, afterId: String?,
    grantedIds: Set<String>, attemptedIds: Set<String>, skippedIds: Set<String>, unavailableIds: Set<String>): String? {
    val start = if (afterId == null) 0 else inventory.indexOfFirst { it.id == afterId }.let { if (it < 0) 0 else it + 1 }
    return inventory.drop(start).firstOrNull {
        it.id !in grantedIds && it.id !in attemptedIds && it.id !in skippedIds && it.id !in unavailableIds
    }?.id
}

/**
 * Builds the installed-build request sequence. Special access and picker grants are deliberately
 * excluded: Android owns those separate flows and they are surfaced only by the feature that needs
 * them. Previously declined items are not re-prompted by setup; users can repair them from the
 * capability-specific surface or Android settings.
 */
internal fun guidedCapabilityRequests(
    sdk: Int,
    granted: Set<String>,
    askedBefore: Set<String>,
): List<GuidedCapabilityRequest> {
    val definitions = guidedCapabilityInventory(sdk).filterNot { it.special }
    return definitions.filter { request ->
        request.id !in askedBefore && request.permissions.any { it !in granted }
    }
}
