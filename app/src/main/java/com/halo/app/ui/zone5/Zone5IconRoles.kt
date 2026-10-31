package com.halo.app.ui.zone5

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

internal enum class Zone5IconRole { DEFAULT, FILE, TOOL }

internal fun zone5IconRole(id: String): Zone5IconRole = when {
    id.startsWith("tool_") -> Zone5IconRole.TOOL
    id.startsWith("type_") || id in setOf("library_saved_chat", "office_document", "office_new_note") -> Zone5IconRole.FILE
    else -> Zone5IconRole.DEFAULT
}

/** A separate, theme-derived document ink. Names and document/tool shapes retain their meaning. */
internal fun zone5FileInk(colors: ColorScheme): Color {
    val accent = colors.primary
    val companion = Color(accent.blue, accent.red, accent.green)
    fun contrast(ink: Color): Float {
        val a = ink.luminance(); val b = colors.background.luminance()
        return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
    }
    for (step in 0..20) {
        val ink = lerp(companion, colors.onSurface, step / 20f)
        if (contrast(ink) >= 3f) return ink
    }
    return colors.onSurface
}
