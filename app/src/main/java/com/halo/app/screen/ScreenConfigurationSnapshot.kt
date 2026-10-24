package com.halo.app.screen

/** Immutable owner snapshot for guarded commands; this is not a separate state store. */
internal data class ScreenConfigurationSnapshot(
    val layout: ScreenLayout?, val hidePrivate: Boolean, val keepCharging: Boolean,
    val dimAtNight: Boolean, val topics: String, val presets: List<ScreenLayout>,
    val canUndo: Boolean, val revision: String,
)

internal fun screenConfigurationDigest(value: String): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
