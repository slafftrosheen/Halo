package com.halo.app.ui.toolkit

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.notification.HaloPlanLiveUpdates
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.zone6.Zone6ButtonSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun HaloAtAGlance(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val liveUpdates = remember(context) { HaloPlanLiveUpdates.get(context.applicationContext) }
    val statusUpdates by liveUpdates.enabled.collectAsStateWithLifecycle()
    var showOptions by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun setStatus(value: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try { liveUpdates.setEnabled(value) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) setStatus(true)
    }
    val title = stringResource(R.string.halo_map_title)
    val subtitle = stringResource(R.string.halo_map_help)
    LifePage("at_a_glance", title, subtitle, stringResource(R.string.halo_map_workspace), true, { onOpen("notebook") }, onBack,
        guidanceBlocked = showOptions, extra = listOf(ShellPageAction(
            "page.at_a_glance.preferences", stringResource(R.string.halo_plan_channel), HaloGlyph.SETTINGS) { showOptions = true }
        )) {
        Text(subtitle)
        ToolkitGroup.entries.forEach { group ->
            Text(stringResource(group.label), style = MaterialTheme.typography.titleLarge)
            HaloToolkit.tools.filter { it.group == group }.forEach { tool ->
                OutlinedButton({ onOpen(tool.route) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    com.halo.app.ui.icons.HaloIcon(tool.glyph, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(tool.title), Modifier.weight(1f))
                }
            }
        }
        Text(stringResource(R.string.halo_map_everyday), style = MaterialTheme.typography.titleLarge)
        val places = listOf(
            Triple("screen/sources", R.string.halo_map_widgets, HaloGlyph.INTEGRATIONS),
            Triple("focus", R.string.halo_focus_title, HaloGlyph.PAUSE),
            Triple("family", R.string.halo_family_title, HaloGlyph.HOME),
            Triple("checkin", R.string.halo_checkin_title, HaloGlyph.CALENDAR),
            Triple("on_move", R.string.halo_move_title, HaloGlyph.NEXT),
            Triple("winddown", R.string.halo_winddown_title, HaloGlyph.PAUSE),
            Triple("emergency", R.string.halo_emergency_title, HaloGlyph.SECURITY),
        )
        places.forEach { (route, label, icon) ->
            OutlinedButton({ onOpen(route) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                com.halo.app.ui.icons.HaloIcon(icon, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(label), Modifier.weight(1f))
            }
        }
        TextButton({ showOptions = true }) { Text(stringResource(R.string.halo_plan_channel)) }
    }
    if (showOptions) Zone6ButtonSheet(stringResource(R.string.halo_plan_channel), { showOptions = false }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.halo_plan_notice_settings))
            com.halo.app.ui.workspace.HaloWorkspaceToggleRow(
                title = stringResource(R.string.halo_plan_notice_toggle), checked = statusUpdates,
                onCheckedChange = { next ->
                    if (next && Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(context).areNotificationsEnabled())
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else setStatus(next)
                })
            if (error) Text(stringResource(R.string.page_layout_error), color = MaterialTheme.colorScheme.error)
        }
    }
}
