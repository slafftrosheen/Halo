package com.halo.app.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*

/** Layout only; effects and drafts remain with the canonical current-entry owners. */
@Composable
internal fun HaloZone(
    route: String?, availableHeight: Dp, compact: Boolean,
    actions: List<HaloOrbContextAction>, onAction: (HaloOrbContextAction) -> Unit,
    onNavigate: (String) -> Unit, attentionCount: Int,
    utility: @Composable () -> Unit, orb: @Composable (Dp) -> Unit,
    profile: @Composable () -> Unit = { com.halo.app.ui.components.PrivacyModeControl(detail = true) },
    backdrop: @Composable BoxScope.() -> Unit = {
        com.halo.app.ui.theme.HaloAmbientBackdrop(Modifier.matchParentSize(), animate = false)
    },
    destinations: List<HaloOrbDestination> = ShellOrbCatalog.globalDestinations(false),
    profileStatus: @Composable () -> Unit = {},
    composer: Boolean = false,
    entryId: String? = null,
) {
    val largeText = LocalDensity.current.fontScale >= 1.4f
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints {
            val fallback = compact || largeText || maxWidth < 360.dp || availableHeight < 520.dp
            val header = LocalShellUtilityBand.current?.value?.takeIf { LocalShellPageOwnerFilter.current(it.owner) }
            val contextual = com.halo.app.ui.zone5.zone5InstrumentOrbitActions(actions).filterNot {
                it.glyph == HaloGlyph.TRASH || it.id in setOf("page-context", "type-message")
            }
            val orbitActions = if (header == null) contextual else contextual.filterNot {
                it.id.startsWith("page:") && it.title == "Back" && it.glyph == HaloGlyph.PREVIOUS
            }
            val navigation = HaloZone5Navigation(route, destinations, onNavigate, attentionCount > 0, profile, profileStatus, entryId)
            backdrop()
            CompositionLocalProvider(LocalHaloZone5Navigation provides navigation) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Header Back stays directly reachable. Matching tools remain in the full action list.
                    CompositionLocalProvider(LocalShellCarouselBackVisible provides false,
                        LocalShellCarouselActionIds provides (if (fallback) emptySet() else orbitActions
                            .filter { it.enabled && it.id.startsWith("page:") }.map { it.id.removePrefix("page:") }.toSet())) { utility() }
                    val size = if (fallback) 48.dp else if (composer) 64.dp else 72.dp
                    if (fallback) orb(size)
                    else ContextToolOrbit(orbitActions, onAction, orb, size, "$entryId:$route",
                        (availableHeight * .25f - 16.dp - if (composer) 48.dp else 0.dp).coerceIn(180.dp, 232.dp))
                }
            }
        }
    }
}
