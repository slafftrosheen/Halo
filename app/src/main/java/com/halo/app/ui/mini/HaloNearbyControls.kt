package com.halo.app.ui.mini

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.screens.settings.SettingsLink
import com.halo.app.ui.screens.settings.HaloSettingsSection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.language.uiText
import com.halo.app.nativeactions.HaloNativeWidget

@Composable
fun HaloNearbyControls() {
    val context = LocalContext.current
    val running by FloatingHaloService.running.collectAsStateWithLifecycle()
    val failure by FloatingHaloService.failure.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<String?>(null) }
    fun show() {
        try { context.startForegroundService(Intent(context, FloatingHaloService::class.java)); notice = null }
        catch (_: Exception) { notice = "Could not show floating Halo. Try again." }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) show() else notice = "Allow display over other apps to show the orb."
    }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(uiText("Keep Halo close with a small window or a home-screen widget."), style = MaterialTheme.typography.bodyMedium)
        Surface(shape = com.halo.app.ui.design.HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                com.halo.app.ui.presence.HaloSignalFace(com.halo.app.ui.presence.HaloSignalPresentation(label = "Mini Halo"), size = 80.dp, contentDescription = uiText("Mini Halo"))
                Text(uiText("A little space for a quick question."), style = MaterialTheme.typography.titleMedium)
                Button(onClick = { context.startActivity(MiniHaloEntry.intent(context)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    HaloIcon(HaloGlyph.HALO, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
                    Text(uiText("Open Mini Halo"))
                }
            }
        }
        HaloSettingsSection(uiText("On your home screen")) {
        SettingsLink(HaloGlyph.HALO, if (running) "Hide floating Halo" else "Show floating Halo",
            "A small orb over other apps. You can hide it anytime.", {
            if (running) context.stopService(Intent(context, FloatingHaloService::class.java))
            else if (Settings.canDrawOverlays(context)) show()
            else runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                .onFailure { notice = "Android settings could not be opened." }
        })
        SettingsLink(HaloGlyph.PHONE, "Add Halo widget", "Quick access without opening the full app.", {
            val manager = AppWidgetManager.getInstance(context)
            if (manager.isRequestPinAppWidgetSupported) {
                if (!manager.requestPinAppWidget(ComponentName(context, HaloNativeWidget::class.java), null, null))
                    notice = "Add Halo from your launcher's widget picker."
            } else notice = "Add Halo from your launcher's widget picker."
        })
        }
        (notice ?: failure)?.let { Text(uiText(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
