package com.halo.app.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
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
    BoxWithConstraints {
        val fallback = singleRow || largeText || maxWidth < 360.dp
        val bounded = !fallback
        val header = LocalShellUtilityBand.current?.value?.takeIf { LocalShellPageOwnerFilter.current(it.owner) }
        val workspace = header?.owner?.routeFamily in workspaceMenuFamilies
        val orbitActions = zone5InstrumentOrbitActions(actions).filterNot {
            it.glyph == HaloGlyph.TRASH || it.id in setOf("page-context", "type-message") ||
                it.id.endsWith(".back") || it.title == "Back" && it.glyph == HaloGlyph.PREVIOUS
        }
        val density = LocalDensity.current
        var utilityHeight by remember(entryId) { mutableStateOf<Dp?>(null) }
        var menuHeight by remember(entryId) { mutableStateOf<Dp?>(null) }
        val spareForHint = bounded && workspace && utilityHeight != null && menuHeight != null &&
            availableHeight * .25f - 77.dp - (utilityHeight ?: 0.dp) - (menuHeight ?: 0.dp) >= 24.dp
        val session = LocalZone5GuideSession.current
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        val visual = com.halo.app.ui.theme.rememberVisualPreferences()
        var disclosed by remember(entryId) { mutableStateOf(false) }
        var toolMenuOpen by remember(entryId) { mutableStateOf(false) }
        var visibleGuide by remember(entryId) { mutableStateOf<String?>(null) }
        var coachmarkId by remember(entryId) { mutableStateOf<String?>(null) }
        val candidate = nextStep?.takeIf { guide -> zone5SafeGuideAction(guide.actionId) &&
            (if (workspace) actions else orbitActions).any { it.enabled && it.id.removePrefix("page:") == guide.actionId } }
        val allowed = zone5GuideAllowed(guideIdle, lifecycle.isAtLeast(Lifecycle.State.RESUMED), windowFocused,
            fallback, WindowInsets.ime.getBottom(LocalDensity.current) > 0, disclosed || toolMenuOpen, visual.guideLight,
            candidate != null && session != null && !session.suppressed(candidate.id))
        LaunchedEffect(candidate, allowed, session) {
            if (!allowed) { visibleGuide = null; coachmarkId = null }
            else if (session != null) {
                kotlinx.coroutines.delay(1200)
                visibleGuide = candidate?.id
            }
        }
        val guide = candidate?.takeIf { allowed && visibleGuide == it.id && session?.suppressed(it.id) == false }
        LaunchedEffect(guide?.id, spareForHint, session) {
            coachmarkId = null
            if (guide != null && spareForHint && session != null && !session.shownThisSession) {
                coachmarkId = guide.id
                session.shown()
                kotlinx.coroutines.delay(6000)
                coachmarkId = null
            }
        }
        val dispatch: (HaloOrbContextAction) -> Unit = { action ->
            guide?.takeIf { action.enabled && it.actionId == action.id.removePrefix("page:") }?.let { session?.dismiss(it.id) }
            onAction(action)
        }
        val navigation = HaloZone5Navigation(route, destinations, onNavigate, attentionCount > 0, profile, profileStatus,
            entryId, compact = fallback, onDisclosureChanged = { disclosed = it }, inlineControls = true,
            compactUtility = { if (singleRow && !composer && header?.owner?.routeFamily != "notebook") utility() })
        val utilityScroll = rememberScrollState()
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().then(if (bounded) Modifier.height(availableHeight * .25f) else Modifier)
                .semantics { testTagsAsResourceId = true }.testTag("halo-zone5")) {
            Box {
                backdrop()
                CompositionLocalProvider(LocalHaloZone5Navigation provides navigation,
                    LocalShellCarouselBackVisible provides true, LocalShellCarouselActionIds provides emptySet()) {
                    Column(Modifier.fillMaxWidth().then(if (bounded) Modifier.fillMaxHeight() else Modifier)
                        .padding(horizontal = 12.dp, vertical = 4.dp)) {
                        HorizontalDivider(color = dividerColor)
                        // The utility keeps one composition call site across keyboard transitions.
                        // Overflow scrolls within the Zone; the working viewport never loses its 3/4 share.
                        Column((if (bounded) Modifier.weight(1f) else Modifier).fillMaxWidth()
                            .then(if (bounded) Modifier.verticalScroll(utilityScroll) else Modifier)) {
                            Column(Modifier.fillMaxWidth().onSizeChanged { utilityHeight = with(density) { it.height.toDp() } }) {
                                if (composer || !singleRow) utility()
                            }
                            if (!singleRow && workspace) Box(Modifier.fillMaxWidth().onSizeChanged {
                                menuHeight = with(density) { it.height.toDp() }
                            }) { WorkspaceToolMenus(guide?.actionId) { toolMenuOpen = it } }
                            else if (!fallback && orbitActions.isNotEmpty()) ContextToolOrbit(orbitActions, dispatch, {}, 64.dp,
                                "$entryId:$route", minimumHeight = 0.dp, guidedActionId = guide?.actionId, dense = true)
                            guide?.takeIf { spareForHint && coachmarkId == it.id }?.let { hint ->
                                Zone5GuideInline(hint) { session?.dismiss(hint.id); visibleGuide = null }
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(top = if (singleRow) 2.dp else 4.dp)) {
                            orb(if (fallback) 48.dp else 64.dp)
                        }
                    }
                }
            }
        }
    }
}
