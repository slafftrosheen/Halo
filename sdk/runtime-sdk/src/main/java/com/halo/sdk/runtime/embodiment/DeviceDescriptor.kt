package com.halo.sdk.runtime.embodiment

/**
 * Persisted device family, NOT a guarantee of hardware readiness.
 *
 * Unknown eyewear must never be silently routed to the HeyCyan driver.
 * These types allow per-vendor adapters to be registered independently; only
 * HEYCYAN_GLASSES and BUDDYBOT currently have Halo production sessions.
 */
enum class DeviceKind(val label: String) {
    HEYCYAN_GLASSES("HeyCyan-compatible glasses"),
    EYEVUE_GLASSES("EyeVue glasses"),
    TUNEBUDS_GLASSES("TuneBuds / AB Mate"),
    MYVU_GLASSES("Meizu MYVU / Star Air"),
    MOYOUNG_W620_GLASSES("MoYoung / W620"),
    META_RAYBAN_GLASSES("Meta Ray-Ban"),
    MENTRA_LIVE_GLASSES("Mentra Live"),
    MEMOMIND_GLASSES("MemoMind / XGIMI"),
    GENERIC_AUDIO("Bluetooth audio"),
    BUDDYBOT("BuddyBot"),
    UNKNOWN("Unknown");

    companion object {
        /**
         * Recognize only exact family identities/explicit aliases.
         * The display name is user-editable and must NEVER select a driver.
         *
         * COLMI A2 uses the HeyCyan companion app, but real protocol support
         * still requires a matching GATT profile and device READY evidence.
         */
        fun fromDeviceType(deviceType: String): DeviceKind {
            val type = deviceType.trim().uppercase(java.util.Locale.ROOT)
                .replace(Regex("[^A-Z0-9]+"), "_").trim('_')
            return when (type) {
                "HEYCYAN", "HEY_CYAN", "HEYCYAN_GLASSES", "COLMI_A2",
                "BW_AG1", "BW_AG1_PRO" -> HEYCYAN_GLASSES
                "EYEVUE", "EYEVUE_GLASSES" -> EYEVUE_GLASSES
                "TUNEBUDS", "AB_MATE", "XK_ONE", "TUNEBUDS_GLASSES" -> TUNEBUDS_GLASSES
                "MEIZU_MYVU", "MEIZU_MYVU_GLASSES", "MYVU", "MYVU_GLASSES",
                "STAR_AIR" -> MYVU_GLASSES
                "MOYOUNG", "MOYOUNG_W620", "MOYOUNG_W620_GLASSES",
                "W620" -> MOYOUNG_W620_GLASSES
                "META_RAYBAN", "META_RAYBAN_GLASSES", "RAY_BAN_META",
                "META_RAY_BAN" -> META_RAYBAN_GLASSES
                "MENTRA", "MENTRA_LIVE", "MENTRA_LIVE_GLASSES" -> MENTRA_LIVE_GLASSES
                "MEMOMIND", "MEMOMIND_GLASSES", "XGIMI_MEMOMIND" -> MEMOMIND_GLASSES
                "GENERIC_AUDIO", "BLUETOOTH_AUDIO", "AUDIO_ONLY" -> GENERIC_AUDIO
                "BUDDYBOT", "BUDDY_BOT" -> BUDDYBOT
                else -> UNKNOWN
            }
        }
    }
}

data class DeviceCapabilities(
    val hasCamera: Boolean = false,
    val hasMicrophone: Boolean = false,
    val hasSpeaker: Boolean = false,
    val hasDisplay: Boolean = false,
    val hasLocomotion: Boolean = false,
    val hasVideoRecording: Boolean = false,
    val hasAudioRecording: Boolean = false
) {
    companion object {
        val HEYCYAN_GLASSES = DeviceCapabilities(
            hasCamera = true,
            hasMicrophone = true,
            hasSpeaker = true,
            hasDisplay = false,
            hasLocomotion = false,
            hasVideoRecording = true,
            hasAudioRecording = true
        )
        val BUDDYBOT = DeviceCapabilities(
            hasCamera = true,
            hasMicrophone = true,
            hasSpeaker = true,
            hasDisplay = true,
            hasLocomotion = true,
            hasVideoRecording = false,
            hasAudioRecording = false
        )
    }
}

data class DeviceDescriptor(
    val kind: DeviceKind,
    val capabilities: DeviceCapabilities
) {
    companion object {
        fun fromKind(kind: DeviceKind): DeviceDescriptor = DeviceDescriptor(
            kind = kind,
            capabilities = when (kind) {
                DeviceKind.HEYCYAN_GLASSES -> DeviceCapabilities.HEYCYAN_GLASSES
                DeviceKind.BUDDYBOT -> DeviceCapabilities.BUDDYBOT
                // Vendor adapters must publish capabilities from a verified session.
                // A known family name alone must not unlock camera/media controls.
                else -> DeviceCapabilities()
            }
        )

        fun fromDeviceType(deviceType: String): DeviceDescriptor =
            fromKind(DeviceKind.fromDeviceType(deviceType))
    }
}

data class DeviceHealth(
    val state: State = State.HEALTHY,
    val message: String? = null
) {
    enum class State { HEALTHY, WARNING, ERROR, UNBOUND }
}

sealed interface DeviceTelemetry {
    val batteryPercent: Int?
    val batteryCharging: Boolean
    val batteryTimestampMs: Long
    val health: DeviceHealth
    val firmwareVersion: String?

    data class Glasses(
        val batteryLevel: Int? = null,
        val charging: Boolean = false,
        val timestampMs: Long = 0L,
        override val health: DeviceHealth = DeviceHealth(),
        override val firmwareVersion: String? = null,
        val wearState: String = "Unknown",
        val volumeMusic: Int? = null,
        val volumeCall: Int? = null
    ) : DeviceTelemetry {
        override val batteryPercent get() = batteryLevel
        override val batteryCharging get() = charging
        override val batteryTimestampMs get() = timestampMs
    }

    data class Robot(
        override val batteryPercent: Int? = null,
        override val batteryCharging: Boolean = false,
        override val batteryTimestampMs: Long = 0L,
        override val health: DeviceHealth = DeviceHealth(),
        override val firmwareVersion: String? = null,
        val safetyState: String = "Healthy",
        val safetyFault: String? = null,
        val forwardMotionBlocked: Boolean = false
    ) : DeviceTelemetry
}
