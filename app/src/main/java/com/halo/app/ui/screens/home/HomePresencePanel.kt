package com.halo.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.fx.*
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.sdk.provider.CognitiveSessionState

/** The large ready presence yields to the conversation and compact runtime status. */
@Composable
internal fun HomePresencePanel(
    signal: HaloSignalPresentation,
    hero: Boolean,
    aura: Boolean,
    micOpen: Boolean,
    level: Float,
    needsSetup: Boolean,
) {
    val accent = MaterialTheme.colorScheme.primary
    if (hero) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            val heroSize = when {
                fontScale > 1.3f -> 160.dp
                configuration.screenHeightDp >= 800 -> 224.dp
                else -> 192.dp
            }
            Box(Modifier.size(heroSize), contentAlignment = Alignment.Center) {
                HaloSignalFace(signal, size = heroSize,
                    modifier = if (aura) Modifier.haloAura(accent) else Modifier)
                HaloRipple(micOpen, level, accent, ringSize = heroSize)
            }
            val ready = signal.state == com.halo.app.ui.presence.HaloSignalState.IDLE
            val greeting = when (java.time.LocalTime.now().hour) {
                in 5..11 -> "Good morning"
                in 12..17 -> "Good afternoon"
                else -> "Good evening"
            }
            Text(if (ready) uiText(greeting) else signal.label, modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.headlineLarge)
            if (ready) Text(signal.label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Surface(Modifier.fillMaxWidth(), shape = HaloShapes.card,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    HaloSignalFace(signal, size = 64.dp)
                    HaloRipple(micOpen, level, accent, ringSize = 76.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(signal.label, style = MaterialTheme.typography.titleLarge)
                    if (needsSetup) Text(uiText("Choose how halo answers"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Exact known actions only. A tool being dispatched is never a success signal. */
internal fun homeToolMoment(toolId: String): Pair<HaloFxTool, String>? = when (toolId) {
    "halo_timer" -> HaloFxTool.TIMER to "Setting a timer"
    "halo_search" -> HaloFxTool.SEARCH to "Opening your search"
    "halo_open_app" -> HaloFxTool.OPEN_APP to "Opening the app"
    else -> null
}

internal fun isPhoneAction(toolId: String?): Boolean = toolId in setOf(
    "halo_open_app", "halo_search", "halo_ui_click", "halo_ui_scroll",
    "halo_ui_set_text", "halo_ui_set_checked", "halo_ui_global_action", "halo_ui_search",
)

@Composable
internal fun HomeToolStatus(tool: CognitiveSessionState.ExecutingTool) {
    val moment = homeToolMoment(tool.toolId)
    Surface(Modifier.fillMaxWidth(), shape = HaloShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (moment != null) HaloToolFx(moment.first, HaloFxToolPhase.WORKING, Modifier.size(56.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText(moment?.second ?: "Working on it"), style = MaterialTheme.typography.titleMedium)
                Text(uiText("You can stop this at any time."), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
