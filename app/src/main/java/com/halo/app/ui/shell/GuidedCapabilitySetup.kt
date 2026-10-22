package com.halo.app.ui.shell

/** One user-explained Android runtime capability request in the resumable setup pass. */
internal data class GuidedCapabilityRequest(
    val id: String,
    val permissions: Set<String>,
)

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
    val definitions = buildList {
        add(GuidedCapabilityRequest("microphone", setOf("android.permission.RECORD_AUDIO")))
        if (sdk >= 33) add(GuidedCapabilityRequest("notifications", setOf("android.permission.POST_NOTIFICATIONS")))
        add(GuidedCapabilityRequest("contacts", setOf("android.permission.READ_CONTACTS")))
        add(GuidedCapabilityRequest("calendar_read", setOf("android.permission.READ_CALENDAR")))
        add(GuidedCapabilityRequest("calendar_write", setOf("android.permission.WRITE_CALENDAR")))
        add(GuidedCapabilityRequest("camera", setOf("android.permission.CAMERA")))
        add(GuidedCapabilityRequest("location", setOf("android.permission.ACCESS_FINE_LOCATION")))
        add(
            GuidedCapabilityRequest(
                "bluetooth",
                if (sdk >= 31) setOf(
                    "android.permission.BLUETOOTH_SCAN",
                    "android.permission.BLUETOOTH_CONNECT",
                ) else setOf("android.permission.ACCESS_FINE_LOCATION"),
            ),
        )
    }
    return definitions.filter { request ->
        request.id !in askedBefore && request.permissions.any { it !in granted }
    }
}
