package com.halo.app.ui.icons

import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Familiar vector symbols shared by controls and Halo's state indicator. */
internal fun HaloGlyph.vector(): ImageVector = when (this) {
    HaloGlyph.HALO -> Icons.Outlined.RadioButtonUnchecked
    HaloGlyph.HOME -> Icons.Outlined.Home
    HaloGlyph.LISTEN -> Icons.Outlined.Mic
    HaloGlyph.SPEAK -> Icons.Outlined.VolumeUp
    HaloGlyph.THINK -> Icons.Outlined.Lightbulb
    HaloGlyph.SEE -> Icons.Outlined.Visibility
    HaloGlyph.CAMERA -> Icons.Outlined.CameraAlt
    HaloGlyph.RECORD -> Icons.Outlined.FiberManualRecord
    HaloGlyph.ACTION -> Icons.Outlined.PlayCircleOutline
    HaloGlyph.TOOL -> Icons.Outlined.Build
    HaloGlyph.APPROVAL -> Icons.Outlined.VerifiedUser
    HaloGlyph.QUESTION -> Icons.Outlined.HelpOutline
    HaloGlyph.SUCCESS -> Icons.Outlined.CheckCircleOutline
    HaloGlyph.WARNING -> Icons.Outlined.WarningAmber
    HaloGlyph.ERROR -> Icons.Outlined.ErrorOutline
    HaloGlyph.STOP -> Icons.Outlined.StopCircle
    HaloGlyph.MODEL -> Icons.Outlined.Psychology
    HaloGlyph.DOWNLOAD -> Icons.Outlined.Download
    HaloGlyph.LOCAL -> Icons.Outlined.PhoneAndroid
    HaloGlyph.CLOUD -> Icons.Outlined.CloudQueue
    HaloGlyph.PHONE -> Icons.Outlined.PhoneAndroid
    HaloGlyph.GLASSES -> Icons.Outlined.Visibility
    HaloGlyph.ROBOT -> Icons.Outlined.SmartToy
    HaloGlyph.WATCH -> Icons.Outlined.Watch
    HaloGlyph.DEVICE -> Icons.Outlined.Devices
    HaloGlyph.NOTE -> Icons.Outlined.EditNote
    HaloGlyph.MEMORY -> Icons.Outlined.BookmarkBorder
    HaloGlyph.FILE -> Icons.Outlined.Description
    HaloGlyph.CONVERSATION -> Icons.Outlined.ChatBubbleOutline
    HaloGlyph.ACTIVITY -> Icons.Outlined.History
    HaloGlyph.SETTINGS -> Icons.Outlined.Settings
    HaloGlyph.SEARCH -> Icons.Outlined.Search
    HaloGlyph.AUTOMATION -> Icons.Outlined.Schedule
    HaloGlyph.SECURITY -> Icons.Outlined.Lock
    HaloGlyph.STORAGE -> Icons.Outlined.FolderOpen
    HaloGlyph.PLAY -> Icons.Outlined.PlayArrow
    HaloGlyph.PAUSE -> Icons.Outlined.Pause
    HaloGlyph.NEXT -> Icons.Outlined.SkipNext
    HaloGlyph.PREVIOUS -> Icons.Outlined.SkipPrevious
    HaloGlyph.BATTERY -> Icons.Outlined.BatteryFull
    HaloGlyph.BLUETOOTH -> Icons.Outlined.Bluetooth
    HaloGlyph.WIFI -> Icons.Outlined.Wifi
    HaloGlyph.OFFLINE -> Icons.Outlined.CloudOff
}

@Composable
fun HaloGlyphRenderer(
    glyph: HaloGlyph,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    if (glyph == HaloGlyph.HALO) {
        val color = if (tint == Color.Unspecified) LocalContentColor.current else tint
        androidx.compose.foundation.Canvas(modifier.then(Modifier.size(24.dp))) {
            val radius = size.minDimension * 0.36f
            val stroke = size.minDimension * 0.075f
            drawArc(color, 28f, 304f, false,
                topLeft = center - androidx.compose.ui.geometry.Offset(radius, radius),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawCircle(color, stroke * 0.85f, center + androidx.compose.ui.geometry.Offset(radius, 0f))
        }
        return
    }
    Icon(glyph.vector(), contentDescription = null, modifier = modifier,
        tint = if (tint == Color.Unspecified) LocalContentColor.current else tint)
}
