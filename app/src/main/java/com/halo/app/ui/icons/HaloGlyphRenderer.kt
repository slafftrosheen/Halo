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
    HaloGlyph.INTEGRATIONS -> Icons.Outlined.Devices
    HaloGlyph.SKILLS -> Icons.Outlined.Build
    HaloGlyph.DIAGNOSTICS -> Icons.Outlined.HelpOutline
    HaloGlyph.RUNTIME_STATUS -> Icons.Outlined.Settings
    HaloGlyph.ADD_DEVICE -> Icons.Outlined.Devices
    HaloGlyph.OPEN_APP -> Icons.Outlined.PhoneAndroid
    HaloGlyph.READ_MODE -> Icons.Outlined.Description
    HaloGlyph.CALENDAR -> Icons.Outlined.Schedule
    HaloGlyph.NOTIFICATIONS -> Icons.Outlined.History
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
    val resource = when (glyph) {
        HaloGlyph.HALO -> com.halo.app.R.drawable.halo_ic_home
        HaloGlyph.HOME -> com.halo.app.R.drawable.halo_ic_home
        HaloGlyph.LISTEN -> com.halo.app.R.drawable.halo_ic_mic
        HaloGlyph.SPEAK -> com.halo.app.R.drawable.halo_ic_speaker
        HaloGlyph.THINK -> com.halo.app.R.drawable.halo_ic_intelligence
        HaloGlyph.SEE -> com.halo.app.R.drawable.halo_ic_runtime_status
        HaloGlyph.CAMERA -> com.halo.app.R.drawable.halo_ic_camera
        HaloGlyph.RECORD -> com.halo.app.R.drawable.halo_ic_voice
        HaloGlyph.ACTION -> com.halo.app.R.drawable.halo_ic_action_mode
        HaloGlyph.TOOL -> com.halo.app.R.drawable.halo_ic_tool
        HaloGlyph.APPROVAL -> com.halo.app.R.drawable.halo_ic_approval
        HaloGlyph.QUESTION -> com.halo.app.R.drawable.halo_ic_ask_mode
        HaloGlyph.SUCCESS -> com.halo.app.R.drawable.halo_ic_check
        HaloGlyph.WARNING -> com.halo.app.R.drawable.halo_ic_unavailable
        HaloGlyph.ERROR -> com.halo.app.R.drawable.halo_ic_failure
        HaloGlyph.STOP -> com.halo.app.R.drawable.halo_ic_stop
        HaloGlyph.MODEL -> com.halo.app.R.drawable.halo_ic_model
        HaloGlyph.DOWNLOAD -> com.halo.app.R.drawable.halo_ic_download
        HaloGlyph.LOCAL -> com.halo.app.R.drawable.halo_ic_on_device
        HaloGlyph.CLOUD -> com.halo.app.R.drawable.halo_ic_cloud
        HaloGlyph.PHONE -> com.halo.app.R.drawable.halo_ic_phone
        HaloGlyph.GLASSES -> com.halo.app.R.drawable.halo_ic_glasses
        HaloGlyph.ROBOT -> com.halo.app.R.drawable.halo_ic_robot
        HaloGlyph.WATCH -> com.halo.app.R.drawable.halo_ic_watch
        HaloGlyph.DEVICE -> com.halo.app.R.drawable.halo_ic_devices
        HaloGlyph.NOTE -> com.halo.app.R.drawable.halo_ic_notebook
        HaloGlyph.MEMORY -> com.halo.app.R.drawable.halo_ic_memory
        HaloGlyph.FILE -> com.halo.app.R.drawable.halo_ic_file_doc
        HaloGlyph.CONVERSATION -> com.halo.app.R.drawable.halo_ic_conversations
        HaloGlyph.ACTIVITY -> com.halo.app.R.drawable.halo_ic_activity
        HaloGlyph.SETTINGS -> com.halo.app.R.drawable.halo_ic_settings
        HaloGlyph.SEARCH -> com.halo.app.R.drawable.halo_ic_search
        HaloGlyph.AUTOMATION -> com.halo.app.R.drawable.halo_ic_automation
        HaloGlyph.INTEGRATIONS -> com.halo.app.R.drawable.halo_ic_integrations
        HaloGlyph.SKILLS -> com.halo.app.R.drawable.halo_ic_skills
        HaloGlyph.DIAGNOSTICS -> com.halo.app.R.drawable.halo_ic_diagnostics
        HaloGlyph.RUNTIME_STATUS -> com.halo.app.R.drawable.halo_ic_runtime_status
        HaloGlyph.ADD_DEVICE -> com.halo.app.R.drawable.halo_ic_add_device
        HaloGlyph.OPEN_APP -> com.halo.app.R.drawable.halo_ic_open_app
        HaloGlyph.READ_MODE -> com.halo.app.R.drawable.halo_ic_read_mode
        HaloGlyph.CALENDAR -> com.halo.app.R.drawable.halo_ic_calendar
        HaloGlyph.NOTIFICATIONS -> com.halo.app.R.drawable.halo_ic_notifications
        HaloGlyph.SECURITY -> com.halo.app.R.drawable.halo_ic_privacy
        HaloGlyph.STORAGE -> com.halo.app.R.drawable.halo_ic_storage
        HaloGlyph.BATTERY -> com.halo.app.R.drawable.halo_ic_battery_low
        HaloGlyph.BLUETOOTH -> com.halo.app.R.drawable.halo_ic_bluetooth
        HaloGlyph.WIFI -> com.halo.app.R.drawable.halo_ic_lan
        HaloGlyph.OFFLINE -> com.halo.app.R.drawable.halo_ic_disconnected
        else -> null
    }
    if (resource != null) {
        Icon(androidx.compose.ui.res.painterResource(resource), contentDescription = null,
            modifier = modifier, tint = if (tint == Color.Unspecified) LocalContentColor.current else tint)
    } else {
        Icon(glyph.vector(), contentDescription = null, modifier = modifier,
            tint = if (tint == Color.Unspecified) LocalContentColor.current else tint)
    }
}
