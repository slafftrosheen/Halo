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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.halo.app.ui.zone5.*
import com.halo.app.ui.zone6.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.halo.app.ui.presence.*

/** Layout only; effects and drafts remain with the canonical current-entry owners. */
@Composable
internal fun HaloZone(
    route: String?, availableHeight: Dp, compact: Boolean,
    actions: List<HaloOrbContextAction>, onAction: (HaloOrbContextAction) -> Unit,
    onNavigate: (String) -> Unit, attentionCount: Int,
    utility: @Composable () -> Unit, orb: @Composable (Dp) -> Unit,
    profile: @Composable () -> Unit = { com.halo.app.ui.components.PrivacyModeControl(detail = true) },
    backdrop: @Composable BoxScope.() -> Unit = { Zone6DockBackdrop(Modifier.matchParentSize()) },
    destinations: List<HaloOrbDestination> = ShellOrbCatalog.globalDestinations(false),
    profileStatus: @Composable () -> Unit = {},
    composer: Boolean = false,
    entryId: String? = null,
    nextStep: ShellNextStep? = null,
    guideIdle: Boolean = false,
    kids: Boolean = false,
    devicesNeedAttention: Boolean = false,
) {
    val density = LocalDensity.current
    val largeText = density.fontScale >= Zone6DockTokens.largeTextScale
    val singleRow = compact || availableHeight < Zone6DockTokens.shortWindow
    BoxWithConstraints {
        val fallback = singleRow || largeText || maxWidth < 320.dp
        val quietToday = !kids && route == HaloHomeTab.TODAY.route && !fallback
        val header = LocalShellUtilityBand.current?.value?.takeIf { LocalShellPageOwnerFilter.current(it.owner) }
        val primaryUtility = header?.takeIf { it.owner.routeFamily in setOf("notebook", "activity") &&
            (!singleRow || maxWidth >= 418.dp) }
        val dockHeight = availableHeight * Zone6DockTokens.fraction
        val bounded = !fallback && !quietToday && dockHeight >= if (primaryUtility != null) 188.dp else 176.dp
        val dockUtility: (@Composable () -> Unit)? = if (primaryUtility == null) null else {
            { RenderShellUtilityBand(primaryUtility) }
        }
        val showUtility = composer || header?.owner?.routeFamily in setOf("kids", "settings")
        val utilityInSheet = !composer && (!showUtility || singleRow)
        val session = LocalZone5GuideSession.current
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        val visual = com.halo.app.ui.theme.rememberVisualPreferences()
        var disclosed by remember(entryId) { mutableStateOf(false) }
        var visibleGuide by remember(entryId) { mutableStateOf<String?>(null) }
        val owner = LocalShellPageActions.current?.value?.owner?.takeIf { LocalShellPageOwnerFilter.current(it) }
        SideEffect { owner?.let { session?.observe(it) } }
        val guided = owner?.takeIf { session?.applies(it) == true }?.helpSteps?.firstOrNull { !it.complete }
        val candidate = (guided?.let { ShellNextStep(it.id, it.actionId, it.message) } ?: nextStep)?.takeIf { guide ->
            (if (guided != null) zone5RequestedGuideAction(guide.actionId) else zone5SafeGuideAction(guide.actionId)) &&
            actions.any { it.enabled && it.id.removePrefix("page:") == guide.actionId } }
        val allowed = !kids && zone5GuideAllowed(guideIdle && owner?.guidanceBlocked != true, lifecycle.isAtLeast(Lifecycle.State.RESUMED), windowFocused,
            fallback, WindowInsets.ime.getBottom(LocalDensity.current) > 0, disclosed,
            visual.guideLight && (visual.guidanceAmount != com.halo.app.ui.theme.HaloGuidanceAmount.OFF || guided != null),
            candidate != null && session != null && (guided != null || !session.suppressed(candidate.id)))
        LaunchedEffect(candidate, allowed, session) {
            if (!allowed) { visibleGuide = null }
            else if (session != null) {
                kotlinx.coroutines.delay(1200)
                visibleGuide = candidate?.id
            }
        }
        val guide = candidate?.takeIf { allowed && visibleGuide == it.id && (guided != null || session?.suppressed(it.id) == false) }
        val manualTour = com.halo.app.ui.zone6.Zone6GuideReplay.tour != null
        val coach = LocalHaloPageGuidance.current
        val coachAvailable = guideIdle && !disclosed && owner?.guidanceBlocked != true &&
            WindowInsets.ime.getBottom(density) == 0 && windowFocused && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
        val guideTarget = Zone6DockGuide(owner?.routeFamily, session,
            eligible = !kids && coachAvailable && coach?.open != true && (manualTour || !singleRow && visual.guideLight),
            kids = kids, route = route, actions = actions, compact = singleRow)
        HaloPageCoach(route, actions, available = !kids && coachAvailable && !manualTour,
            automatic = !kids && visual.guideLight &&
                visual.guidanceAmount == com.halo.app.ui.theme.HaloGuidanceAmount.EXPLANATIONS,
            composer = composer)
        SideEffect { session?.presentationActive = guideTarget != null || guide != null || disclosed || coach?.open == true }
        DisposableEffect(session) { onDispose { session?.presentationActive = false } }
        val navigation = HaloZone5Navigation(route, destinations, onNavigate, attentionCount > 0, profile, profileStatus,
            entryId, compact = singleRow, onDisclosureChanged = { disclosed = it }, inlineControls = true,
            compactUtility = { if (utilityInSheet) utility() }, kids = kids, guidanceTargetVisible = !fallback,
            compactUtilityIncludesStatus = utilityInSheet && header?.includesStatus == true,
            zone6 = true, attentionCount = attentionCount, composer = composer,
            primaryUtility = dockUtility, quietToday = quietToday, devicesNeedAttention = devicesNeedAttention)
        val utilityScroll = rememberScrollState()
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = Zone6DockTokens.radius, topEnd = Zone6DockTokens.radius),
            modifier = Modifier.fillMaxWidth().then(if (bounded) Modifier.height(dockHeight) else Modifier)
                .haloGestureAnchor(HaloGesture.DOCK_SWIPE)
                .semantics { testTagsAsResourceId = true }.testTag("halo-zone6")) {
            Box {
                backdrop()
                CompositionLocalProvider(LocalHaloZone5Navigation provides navigation, LocalZone6GuideTarget provides guideTarget,
                    LocalShellCarouselBackVisible provides true, LocalShellCarouselActionIds provides emptySet()) {
                    Column(Modifier.fillMaxWidth().then(if (bounded) Modifier.fillMaxHeight() else Modifier)
                        .padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Zone6DockHorizon()
                        if (composer) {
                            HomeComposerDock(
                                modifier = if (bounded) Modifier.weight(1f) else Modifier,
                                singleRow = singleRow,
                                bounded = bounded,
                                place = {
                                    if (!singleRow) Zone6PlaceRow(route, owner ?: header?.owner, guide) {
                                        guide?.let { session?.dismiss(it.id) }
                                        session?.pause()
                                        visibleGuide = null
                                    }
                                },
                                utility = utility,
                                navigation = { orb(if (singleRow) 48.dp else 56.dp) },
                            )
                        } else {
                            Column((if (bounded) Modifier.weight(1f) else Modifier).fillMaxWidth()
                                .then(if (bounded) Modifier.verticalScroll(utilityScroll) else Modifier)) {
                                if (!singleRow && !quietToday) Zone6PlaceRow(route, owner ?: header?.owner, guide) {
                                    guide?.let { session?.dismiss(it.id) }
                                    session?.pause()
                                    visibleGuide = null
                                }
                                if (!utilityInSheet) Column(Modifier.fillMaxWidth()) { utility() }
                            }
                            orb(when { kids -> 60.dp; singleRow || quietToday -> 48.dp; else -> 56.dp })
                        }
                    }
                }
            }
        }
    }
}

/** The editor stays in the same layout slot when the keyboard changes the dock shape. */
@Composable
private fun HomeComposerDock(
    modifier: Modifier,
    singleRow: Boolean,
    bounded: Boolean,
    place: @Composable () -> Unit,
    utility: @Composable () -> Unit,
    navigation: @Composable () -> Unit,
) {
    Layout(modifier = modifier.fillMaxWidth(), content = {
        Box(Modifier.fillMaxWidth()) { place() }
        Box(Modifier.fillMaxWidth().zone6GuideOutline(Zone6GuideTarget.COMPOSER).haloGuideControl("composer")) { utility() }
        Box(Modifier.fillMaxWidth()) { navigation() }
    }) { children, constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minHeight = 0, minWidth = 0)
        val navWidth = if (singleRow) 54.dp.roundToPx().coerceAtMost(width) else width
        val nav = children[2].measure(loose.copy(maxWidth = navWidth))
        val editor = children[1].measure(loose.copy(
            maxWidth = if (singleRow) width - navWidth else width,
            maxHeight = if (singleRow) loose.maxHeight else (loose.maxHeight - nav.height).coerceAtLeast(0),
        ))
        val space = (loose.maxHeight - editor.height - nav.height).coerceAtLeast(0)
        val showPlace = !singleRow && space >= 44.dp.roundToPx()
        val header = children[0].measure(loose.copy(maxHeight = if (showPlace) space else 0))
        val height = constraints.constrainHeight(when {
            singleRow -> maxOf(editor.height, nav.height)
            bounded -> constraints.maxHeight
            else -> header.height + editor.height + nav.height
        })
        layout(width, height) {
            if (singleRow) {
                editor.placeRelative(0, (height - editor.height) / 2)
                nav.placeRelative(width - navWidth, (height - nav.height) / 2)
            } else {
                if (showPlace) header.placeRelative(0, 0)
                editor.placeRelative(0, header.height)
                nav.placeRelative(0, height - nav.height)
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
