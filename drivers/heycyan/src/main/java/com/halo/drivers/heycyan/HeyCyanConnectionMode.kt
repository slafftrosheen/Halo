package com.halo.drivers.heycyan

/**
 * Selects which responsibilities are allowed for a HeyCyan transport session.
 *
 * Discovery is intentionally a raw evidence workflow. Runtime is the only mode
 * that may interpret notifications or attempt an evidence-backed command.
 */
enum class HeyCyanConnectionMode {
    DISCOVERY,
    RUNTIME
}

internal fun canDecodeEvents(mode: HeyCyanConnectionMode): Boolean = mode == HeyCyanConnectionMode.RUNTIME

internal fun canSendCommands(mode: HeyCyanConnectionMode): Boolean = mode == HeyCyanConnectionMode.RUNTIME
