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
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
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
        catch (_: Exception) { notice = "Could not show floating halo. Try again." }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) show() else notice = "Allow display over other apps to show the orb."
    }
    val openMini: () -> Unit = { context.startActivity(MiniHaloEntry.intent(context)) }
    val toggleFloating: () -> Unit = {
        if (running) context.stopService(Intent(context, FloatingHaloService::class.java))
        else if (Settings.canDrawOverlays(context)) show()
        else runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
            .onFailure { notice = "Android settings could not be opened." }
    }
    val addWidget: () -> Unit = {
        val manager = AppWidgetManager.getInstance(context)
        if (manager.isRequestPinAppWidgetSupported) {
            if (!manager.requestPinAppWidget(ComponentName(context, HaloNativeWidget::class.java), null, null))
                notice = "Add halo from your launcher's widget picker."
        } else notice = "Add halo from your launcher's widget picker."
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
            NearbyTile("Mini halo", "Open", HaloGlyph.PHONE, Modifier.fillMaxWidth(), openMini)
            NearbyTile("Floating halo", if (running) "Hide" else "Show", HaloGlyph.HALO, Modifier.fillMaxWidth(), toggleFloating)
            NearbyTile("Widget", "Add", HaloGlyph.HOME, Modifier.fillMaxWidth(), addWidget)
        } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NearbyTile("Mini halo", "Open", HaloGlyph.PHONE, Modifier.weight(1f).fillMaxHeight(), openMini)
            NearbyTile("Floating halo", if (running) "Hide" else "Show", HaloGlyph.HALO, Modifier.weight(1f).fillMaxHeight(), toggleFloating)
            NearbyTile("Widget", "Add", HaloGlyph.HOME, Modifier.weight(1f).fillMaxHeight(), addWidget)
        }
        (notice ?: failure)?.let { Text(uiText(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun NearbyTile(label: String, action: String, glyph: HaloGlyph, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 104.dp), shape = HaloShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloIcon(glyph, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(uiText(label), style = MaterialTheme.typography.labelLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(uiText(action), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
