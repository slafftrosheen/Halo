package com.halo.app.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.presence.HaloOrbContextAction

/** Layout only. All effects remain with the caller's current page and canonical owners. */
@Composable
internal fun HaloZone(
    route: String?, availableHeight: Dp, compact: Boolean,
    actions: List<HaloOrbContextAction>, onAction: (HaloOrbContextAction) -> Unit,
    onNavigate: (String) -> Unit, attentionCount: Int,
    utility: @Composable () -> Unit, orb: @Composable (Dp) -> Unit,
    profile: @Composable () -> Unit = { com.halo.app.ui.components.CompactPrivacyModeControl() },
    backdrop: @Composable BoxScope.() -> Unit = { com.halo.app.ui.theme.HaloAmbientBackdrop(Modifier.matchParentSize()) },
) {
    val largeText = LocalDensity.current.fontScale >= 1.4f
    val fullActionHint = uiText("Hold halo for actions")
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .96f),
        tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints {
        // 16dp padding + two 64dp fixed columns leave 216dp for four
        // independent orbit targets at the smallest normal 360dp viewport.
        val fallback = compact || largeText || maxWidth < 360.dp
        backdrop()
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            CompositionLocalProvider(com.halo.app.ui.presence.LocalShellCarouselBackVisible provides
                (!fallback && actions.any {
                    it.id.startsWith("page:") && it.title == "Back" && it.glyph == HaloGlyph.PREVIOUS && it.enabled
                }), com.halo.app.ui.presence.LocalShellCarouselActionIds provides
                (if (fallback) emptySet() else actions.filter { it.enabled && it.id.startsWith("page:") }
                    .map { it.id.removePrefix("page:") }.toSet())) { utility() }
            if (fallback) {
                if (this@BoxWithConstraints.maxWidth < 320.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            ZoneDestination("home", "Home", HaloGlyph.HOME, route, onNavigate)
                            ZoneDestination("notebook", "Workspace", HaloGlyph.NOTE, route, onNavigate)
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { orb(56.dp) }
                        Column {
                            ZoneDestination("devices", "Devices", HaloGlyph.DEVICE, route, onNavigate)
                            ZoneDestination("settings", "Settings", HaloGlyph.SETTINGS, route, onNavigate)
                        }
                    }
                } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    ZoneDestination("home", "Home", HaloGlyph.HOME, route, onNavigate)
                    ZoneDestination("notebook", "Workspace", HaloGlyph.NOTE, route, onNavigate)
                    Box(Modifier.width((this@BoxWithConstraints.maxWidth - 208.dp).coerceIn(104.dp, 152.dp)), contentAlignment = Alignment.Center) { orb(56.dp) }
                    ZoneDestination("devices", "Devices", HaloGlyph.DEVICE, route, onNavigate)
                    ZoneDestination("settings", "Settings", HaloGlyph.SETTINGS, route, onNavigate)
                }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    ZoneBell(attentionCount, onNavigate)
                    Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.interface4_hold_halo_short),
                        style = MaterialTheme.typography.labelSmall, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics {
                            contentDescription = fullActionHint
                        })
                    Box(Modifier.width(if (this@BoxWithConstraints.maxWidth < 320.dp) 64.dp else 112.dp), contentAlignment = Alignment.Center) { profile() }
                }
            } else {
                Row(Modifier.fillMaxWidth().heightIn(min = (availableHeight * .25f - 56.dp).coerceIn(144.dp, 184.dp)),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ZoneDestination("home", "Home", HaloGlyph.HOME, route, onNavigate)
                        ZoneDestination("notebook", "Workspace", HaloGlyph.NOTE, route, onNavigate)
                        Box(Modifier.heightIn(min = 72.dp), contentAlignment = Alignment.Center) { ZoneBell(attentionCount, onNavigate) }
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        ContextToolOrbit(actions, onAction, orb)
                    }
                    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ZoneDestination("devices", "Devices", HaloGlyph.DEVICE, route, onNavigate)
                        ZoneDestination("settings", "Settings", HaloGlyph.SETTINGS, route, onNavigate)
                        Box(Modifier.heightIn(min = 72.dp), contentAlignment = Alignment.Center) { profile() }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ZoneDestination(destination: String, title: String, glyph: HaloGlyph,
    route: String?, onNavigate: (String) -> Unit) {
    val selected = primaryNavigationRoute(route ?: "home") == destination
    val reduced by com.halo.app.ui.theme.rememberReducedMotion()
    val fill by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(if (reduced) 0 else com.halo.app.ui.design.HaloMotion.selectionMs),
        label = "Fixed destination selection")
    IconButton(onClick = { onNavigate(destination) }, modifier = Modifier.size(48.dp).semantics {
        this.selected = selected; role = Role.Tab
    }, colors = IconButtonDefaults.iconButtonColors(
        containerColor = fill,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)) {
        HaloIcon(glyph, uiText(title), Modifier.size(24.dp))
    }
}

@Composable
private fun ZoneBell(count: Int, onNavigate: (String) -> Unit) {
    IconButton(onClick = { onNavigate("activity") }, modifier = Modifier.size(48.dp)) {
        BadgedBox(badge = { if (count > 0) Badge { Text(count.coerceAtMost(99).toString()) } }) {
            HaloIcon(HaloGlyph.NOTIFICATIONS, uiText("Activities and notifications"), Modifier.size(24.dp))
        }
    }
}
