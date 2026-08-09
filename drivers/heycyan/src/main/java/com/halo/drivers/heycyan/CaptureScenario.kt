package com.halo.drivers.heycyan

/**
 * Controlled physical-capture scenarios for PR-016.
 *
 * The scenario describes the operator action only. It does not imply any
 * protocol meaning for the packets recorded during the session.
 */
enum class CaptureScenario(
    val id: String,
    val label: String,
    val instructions: String
) {
    BOOT(
        id = "boot-001",
        label = "Boot",
        instructions = "Start with the device powered off, power it on, connect, wait for transport readiness, idle for 30-60 seconds, then disconnect."
    ),
    BUTTON_SINGLE(
        id = "button-single-001",
        label = "Single button press",
        instructions = "Connect, wait for transport readiness, perform exactly one single press, wait, then disconnect."
    ),
    BUTTON_DOUBLE(
        id = "button-double-001",
        label = "Double button press",
        instructions = "Connect, wait for transport readiness, perform exactly one double press, wait, then disconnect."
    ),
    BUTTON_LONG(
        id = "button-long-001",
        label = "Long button press",
        instructions = "Connect, wait for transport readiness, perform exactly one long press, wait, then disconnect."
    ),
    BATTERY(
        id = "battery-001",
        label = "Battery and charging",
        instructions = "Connect, observe idle traffic, perform a charging transition if physically possible, observe, then disconnect."
    ),
    RECONNECT(
        id = "reconnect-001",
        label = "Reconnect",
        instructions = "Connect, wait for transport readiness, disconnect, explicitly select the same address again, reconnect, wait, then disconnect."
    );

    companion object {
        fun fromId(id: String): CaptureScenario = entries.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown capture scenario: $id")
    }
}
