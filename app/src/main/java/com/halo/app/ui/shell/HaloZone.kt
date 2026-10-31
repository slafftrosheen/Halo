package com.halo.app.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.halo.app.ui.zone5.*
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
    nextStep: ShellNextStep? = null,
    guideIdle: Boolean = false,
) {
    val largeText = LocalDensity.current.fontScale >= 1.4f
    val singleRow = compact || primaryNavigationRoute(route) == "settings" || availableHeight < 520.dp
    val dividerColor = if (com.halo.app.ui.theme.rememberVisualPreferences().higherContrast)
        MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary.copy(alpha = .24f)
    Surface(color = if (singleRow) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column {
        if (!singleRow) HorizontalDivider(color = dividerColor)
        BoxWithConstraints {
            val fallback = singleRow || largeText || maxWidth < 360.dp
            val header = LocalShellUtilityBand.current?.value?.takeIf { LocalShellPageOwnerFilter.current(it.owner) }
            val contextual = com.halo.app.ui.zone5.zone5InstrumentOrbitActions(actions).filterNot {
                it.glyph == HaloGlyph.TRASH || it.id in setOf("page-context", "type-message")
            }
            val orbitActions = if (header == null) contextual else contextual.filterNot {
                it.id.startsWith("page:") && it.title == "Back" && it.glyph == HaloGlyph.PREVIOUS
            }
            val session = LocalZone5GuideSession.current
            val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
            val windowFocused = LocalWindowInfo.current.isWindowFocused
            val visual = com.halo.app.ui.theme.rememberVisualPreferences()
            var disclosed by remember(entryId) { mutableStateOf(false) }
            var visibleGuide by remember(entryId) { mutableStateOf<String?>(null) }
            var coachmarkId by remember(entryId) { mutableStateOf<String?>(null) }
            val candidate = nextStep?.takeIf { guide -> zone5SafeGuideAction(guide.actionId) &&
                orbitActions.any { it.enabled && it.id.removePrefix("page:") == guide.actionId } }
            val allowed = zone5GuideAllowed(guideIdle, lifecycle.isAtLeast(Lifecycle.State.RESUMED), windowFocused,
                fallback, WindowInsets.ime.getBottom(LocalDensity.current) > 0, disclosed, visual.guideLight,
                candidate != null && session != null && !session.suppressed(candidate.id))
            LaunchedEffect(candidate, allowed, session) {
                if (!allowed) { visibleGuide = null; coachmarkId = null }
                else if (session != null) {
                    kotlinx.coroutines.delay(1200)
                    visibleGuide = candidate?.id
                    if (!session.shownThisSession) { coachmarkId = candidate?.id; session.shown() }
                }
            }
            val guide = candidate?.takeIf { allowed && visibleGuide == it.id && session?.suppressed(it.id) == false }
            val dispatch: (HaloOrbContextAction) -> Unit = { action ->
                guide?.takeIf { action.enabled && it.actionId == action.id.removePrefix("page:") }?.let { session?.dismiss(it.id) }
                onAction(action)
            }
            val navigation = HaloZone5Navigation(route, destinations, onNavigate, attentionCount > 0, profile, profileStatus,
                entryId, singleRow, onDisclosureChanged = { disclosed = it })
            backdrop()
            CompositionLocalProvider(LocalHaloZone5Navigation provides navigation) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (singleRow) 4.dp else 8.dp)) {
                    // Header Back stays directly reachable. Matching tools remain in the full action list.
                    CompositionLocalProvider(LocalShellCarouselBackVisible provides false,
                        LocalShellCarouselActionIds provides (if (fallback) emptySet() else orbitActions
                            .filter { it.enabled && it.id.startsWith("page:") }.map { it.id.removePrefix("page:") }.toSet())) { utility() }
                    val size = if (fallback) 48.dp else if (composer) 64.dp else 72.dp
                    if (singleRow) {
                        HorizontalDivider(color = dividerColor)
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { orb(size) }
                        }
                    } else if (fallback) orb(size)
                    else {
                        guide?.takeIf { coachmarkId == it.id }?.let { Zone5GuideCallout(it) { session?.dismiss(it.id); visibleGuide = null } }
                        ContextToolOrbit(orbitActions, dispatch, orb, size, "$entryId:$route",
                            (availableHeight * .25f - 16.dp - if (composer) 48.dp else 0.dp).coerceIn(180.dp, 232.dp),
                            guidedActionId = guide?.actionId)
                    }
                }
            }
        }
        }
    }
}
