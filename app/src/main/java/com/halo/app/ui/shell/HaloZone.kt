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
    kids: Boolean = false,
    familyControl: @Composable () -> Unit = {},
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
                (header?.owner?.routeFamily == "kids" && it.id.removePrefix("page:") !in setOf("page.kids.type", "page.kids.help")) ||
                it.id.endsWith(".back") || it.title == "Back" && it.glyph == HaloGlyph.PREVIOUS
        }
        val density = LocalDensity.current
        var utilityHeight by remember(entryId) { mutableStateOf<Dp?>(null) }
        var menuHeight by remember(entryId) { mutableStateOf<Dp?>(null) }
        var navigationHeight by remember(entryId, fallback, kids, density.fontScale) { mutableStateOf(if (fallback) 68.dp else 84.dp) }
        var hintHeight by remember(entryId) { mutableStateOf(64.dp) }
        val placement = zone5SecondaryPlacement(availableHeight * .25f, navigationHeight,
            utilityHeight ?: 0.dp, menuHeight ?: if (workspace) 48.dp else if (kids) 56.dp else 64.dp)
        val utilityInSheet = !composer && (singleRow || bounded && placement.utilityInActions)
        val menusInSheet = singleRow || bounded && placement.menusInActions
        val spareForHint = bounded && workspace && utilityHeight != null && menuHeight != null &&
            !utilityInSheet && !menusInSheet && availableHeight * .25f - navigationHeight - 9.dp - (utilityHeight ?: 0.dp) - (menuHeight ?: 0.dp) >= hintHeight
        val session = LocalZone5GuideSession.current
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        val visual = com.halo.app.ui.theme.rememberVisualPreferences()
        var disclosed by remember(entryId) { mutableStateOf(false) }
        var toolMenuOpen by remember(entryId) { mutableStateOf(false) }
        var visibleGuide by remember(entryId) { mutableStateOf<String?>(null) }
        var coachmarkId by remember(entryId) { mutableStateOf<String?>(null) }
        val owner = LocalShellPageActions.current?.value?.owner?.takeIf { LocalShellPageOwnerFilter.current(it) }
        SideEffect { owner?.let { session?.observe(it) } }
        val guided = owner?.takeIf { session?.applies(it) == true }?.helpSteps?.firstOrNull { !it.complete }
        val candidate = (guided?.let { ShellNextStep(it.id, it.actionId, it.message) } ?: nextStep)?.takeIf { guide ->
            (if (guided != null) zone5RequestedGuideAction(guide.actionId) else zone5SafeGuideAction(guide.actionId)) &&
            (if (workspace) actions else orbitActions).any { it.enabled && it.id.removePrefix("page:") == guide.actionId } }
        val allowed = zone5GuideAllowed(guideIdle && owner?.guidanceBlocked != true, lifecycle.isAtLeast(Lifecycle.State.RESUMED), windowFocused,
            fallback, WindowInsets.ime.getBottom(LocalDensity.current) > 0, disclosed || toolMenuOpen,
            visual.guideLight && (visual.guidanceAmount != com.halo.app.ui.theme.HaloGuidanceAmount.OFF || guided != null),
            candidate != null && !menusInSheet && session != null && (guided != null || !session.suppressed(candidate.id)))
        LaunchedEffect(candidate, allowed, session) {
            if (!allowed) { visibleGuide = null; coachmarkId = null }
            else if (session != null) {
                kotlinx.coroutines.delay(1200)
                visibleGuide = candidate?.id
            }
        }
        val guide = candidate?.takeIf { allowed && visibleGuide == it.id && (guided != null || session?.suppressed(it.id) == false) }
        SideEffect { session?.presentationActive = guide != null || disclosed || toolMenuOpen }
        DisposableEffect(session) { onDispose { session?.presentationActive = false } }
        val accessibility = androidx.compose.ui.platform.LocalAccessibilityManager.current
        LaunchedEffect(guide?.id, spareForHint, session) {
            coachmarkId = null
            if (guide != null && spareForHint && visual.guidanceAmount == com.halo.app.ui.theme.HaloGuidanceAmount.EXPLANATIONS && session != null && !session.shownThisSession) {
                coachmarkId = guide.id
                session.shown()
                kotlinx.coroutines.delay(accessibility?.calculateRecommendedTimeoutMillis(6000, containsText = true, containsControls = true) ?: 6000)
                coachmarkId = null
            }
        }
        val dispatch: (HaloOrbContextAction) -> Unit = { action ->
            onAction(action)
        }
        val navigation = HaloZone5Navigation(route, destinations, onNavigate, attentionCount > 0, profile, profileStatus,
            entryId, compact = fallback, onDisclosureChanged = { disclosed = it }, inlineControls = true,
            compactUtility = { if (utilityInSheet && header?.owner?.routeFamily != "notebook") utility() }, kids = kids, guidanceTargetVisible = !fallback && !menusInSheet,
            compactUtilityIncludesStatus = utilityInSheet && header?.owner?.routeFamily != "notebook" && header?.includesStatus == true,
            familyControl = familyControl)
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
                            if (!utilityInSheet) Column(Modifier.fillMaxWidth().testTag("zone5-utility").onSizeChanged { utilityHeight = with(density) { it.height.toDp() } }) {
                                utility()
                            }
                            if (utilityInSheet && bounded) header?.owner?.statusLabel?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth())
                            }
                            if (!menusInSheet && workspace) Box(Modifier.fillMaxWidth().testTag("zone5-command-groups").onSizeChanged {
                                menuHeight = with(density) { it.height.toDp() }
                            }) { WorkspaceToolMenus(guide?.actionId) { toolMenuOpen = it } }
                            else if (!fallback && !menusInSheet && orbitActions.isNotEmpty()) Box(Modifier.fillMaxWidth().onSizeChanged {
                                menuHeight = with(density) { it.height.toDp() }
                            }) {
                                if (kids) KidsZoneActionStrip(orbitActions, dispatch, guide?.actionId)
                                else ContextToolOrbit(orbitActions, dispatch, {}, 64.dp,
                                    "$entryId:$route", minimumHeight = 0.dp, guidedActionId = guide?.actionId, dense = true, minimumTarget = 48.dp)
                            }
                            guide?.takeIf { spareForHint && coachmarkId == it.id }?.let { hint ->
                                Box(Modifier.onSizeChanged { hintHeight = with(density) { it.height.toDp() } }) {
                                    Zone5GuideInline(hint) { session?.dismiss(hint.id); session?.pause(); visibleGuide = null }
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().onSizeChanged { navigationHeight = with(density) { it.height.toDp() } }
                            .padding(top = if (singleRow) 2.dp else 0.dp)) {
                            orb(if (fallback) { if (kids) 56.dp else 48.dp } else 64.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Fit secondary controls before any scrolling is necessary; the primary row never shrinks. */
internal data class Zone5SecondaryPlacement(val menusInActions: Boolean, val utilityInActions: Boolean)
internal fun zone5SecondaryPlacement(zone: Dp, navigation: Dp, utility: Dp, menus: Dp): Zone5SecondaryPlacement {
    val available = zone - navigation - 9.dp // divider and vertical padding
    val moveMenus = utility + menus > available
    return Zone5SecondaryPlacement(moveMenus, utility > available)
}
