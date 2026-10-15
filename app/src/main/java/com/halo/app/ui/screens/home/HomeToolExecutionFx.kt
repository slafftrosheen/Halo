package com.halo.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.fx.HaloFxTool
import com.halo.app.ui.fx.HaloFxToolPhase
import com.halo.app.ui.fx.HaloToolFx
import kotlinx.coroutines.delay

/**
 * v1.2 tool FX is NOT inferred from a prompt or a configured skill. The caller
 * mounts this only for CognitiveSessionState.ExecutingTool, which reports
 * the exact tool currently owned by the session. Dispatch is not completion.
 */
internal fun homeFxTool(toolId: String): HaloFxTool? = when (toolId) {
    "halo_timer", "halo_set_alarm", "halo_clock_modify" -> HaloFxTool.TIMER
    "halo_search", "halo_browser", "halo_fetch_url" -> HaloFxTool.SEARCH
    "halo_open_app", "halo_open_screen", "halo_current_app_action" -> HaloFxTool.OPEN_APP
    "halo_calendar_create", "halo_calendar_editor", "halo_calendar_upcoming" -> HaloFxTool.CALENDAR
    "halo_compose_sms", "halo_compose_email", "halo_compose_message",
    "halo_reply_notification" -> HaloFxTool.SEND
    // The camera FX is reserved until a camera-action tool exposes its own
    // execution ID. Tapping a camera picker is not a camera execution receipt.
    else -> null
}

/** Phone-action pulse never runs for a general model turn or an arbitrary tool. */
internal fun homeIsPhoneAction(toolId: String): Boolean =
    toolId.startsWith("halo_ui_") && toolId != "halo_ui_wait" ||
        toolId in setOf(
            "halo_open_app", "halo_open_screen", "halo_current_app_action",
            "halo_perform_app_action", "halo_find_on_screen",
        )

internal fun homeFxToolLabel(tool: HaloFxTool): String = when (tool) {
    HaloFxTool.TIMER -> "Timer"
    HaloFxTool.SEARCH -> "Search"
    HaloFxTool.OPEN_APP -> "Open app"
    HaloFxTool.CAMERA -> "Camera"
    HaloFxTool.CALENDAR -> "Calendar"
    HaloFxTool.SEND -> "Send"
}

@Composable
internal fun HomeToolExecutionFx(toolId: String) {
    val tool = homeFxTool(toolId) ?: return
    var phase by remember(toolId) { mutableStateOf(HaloFxToolPhase.START) }
    LaunchedEffect(toolId) {
        delay(400L)
        phase = HaloFxToolPhase.WORKING
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            key(toolId) {
                HaloToolFx(tool, phase, modifier = Modifier.size(64.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText("Working on it…"), style = MaterialTheme.typography.titleSmall)
                Text(uiText(homeFxToolLabel(tool)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
