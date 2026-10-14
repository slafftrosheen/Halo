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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Keep Halo close with a small window or a home-screen widget."), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { context.startActivity(MiniHaloEntry.intent(context)) }, modifier = Modifier.fillMaxWidth()) { Text(uiText("Open Mini Halo")) }
        OutlinedButton(onClick = {
            if (running) context.stopService(Intent(context, FloatingHaloService::class.java))
            else if (Settings.canDrawOverlays(context)) show()
            else runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                .onFailure { notice = "Android settings could not be opened." }
        }, modifier = Modifier.fillMaxWidth()) { Text(uiText(if (running) "Hide floating Halo" else "Show floating Halo")) }
        OutlinedButton(onClick = {
            val manager = AppWidgetManager.getInstance(context)
            if (manager.isRequestPinAppWidgetSupported) {
                if (!manager.requestPinAppWidget(ComponentName(context, HaloNativeWidget::class.java), null, null))
                    notice = "Add Halo from your launcher's widget picker."
            } else notice = "Add Halo from your launcher's widget picker."
        }, modifier = Modifier.fillMaxWidth()) { Text(uiText("Add Halo widget")) }
        (notice ?: failure)?.let { Text(uiText(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
