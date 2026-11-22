package com.halo.app.ui.presence


import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.shell.primaryNavigationRoute
import com.halo.app.ui.zone5.Zone5Icon
import com.halo.app.ui.zone5.zone5ActionIconId
import com.halo.app.ui.zone5.workspaceMenuFamilies
import com.halo.app.ui.zone5.workspaceMenuGroup
import com.halo.app.ui.zone5.WorkspaceMenuGroup
import com.halo.app.ui.zone6.*

/** Transient host presentation. Domain, navigation and privacy authorities remain at the caller. */
internal class HaloZone5Navigation(
    val route: String?, val destinations: List<HaloOrbDestination>, val onNavigate: (String) -> Unit,
    val hasUpdates: Boolean, val profileControl: @Composable () -> Unit,
    val profileStatus: @Composable () -> Unit,
    val entryId: String? = null,
    val compact: Boolean = false,
    val onDisclosureChanged: (Boolean) -> Unit = {},
    val inlineControls: Boolean = false,
    val compactUtility: @Composable () -> Unit = {},
    val kids: Boolean = false,
    val guidanceTargetVisible: Boolean = false,
    val compactUtilityIncludesStatus: Boolean = false,
    val zone6: Boolean = false,
    val attentionCount: Int = 0,
    val composer: Boolean = false,
    val primaryUtility: (@Composable () -> Unit)? = null,
    val quietToday: Boolean = false,
    val devicesNeedAttention: Boolean = false,
)
internal val LocalHaloZone5Navigation = staticCompositionLocalOf<HaloZone5Navigation?> { null }

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun HaloZone5OrbLauncher(
    signal: HaloSignalPresentation, interaction: HaloOrbInteraction,
    onPrimary: (HaloOrbPrimary) -> Unit, onStop: () -> Unit,
    actions: List<HaloOrbContextAction>, onAction: (HaloOrbContextAction) -> Unit,
    size: Dp, orbModifier: Modifier, navigation: HaloZone5Navigation,
    actionPins: com.halo.app.ui.theme.ActionPinSettings = com.halo.app.ui.theme.ActionPinSettings(),
    onActionPins: (suspend (String, List<String>, List<String>) -> Unit)? = null,
) {
    val routeFamily = navigation.route?.substringBefore('?')
    val pauseContext = LocalContext.current
    val paused by remember(pauseContext) { com.halo.app.privacy.HaloPauseOwner.get(pauseContext).state }.collectAsStateWithLifecycle()
    val pausedLabel = stringResource(R.string.halo_pause_status)
    val recording = if (routeFamily == "record") {
        com.halo.app.nativeactions.EverydayWidgetRecording.active.collectAsStateWithLifecycle().value
    } else false
    val focusActive = if (routeFamily == "focus") {
        val context = LocalContext.current
        val state = remember(context) { com.halo.app.ui.toolkit.HaloFocusOwner.get(context).state }
        state.collectAsStateWithLifecycle().value?.left()?.let { it > 0L } == true
    } else false
    val shownSignal = when {
        paused.paused -> signal.copy(state = HaloSignalState.STOPPED, label = pausedLabel, glyph = HaloGlyph.PAUSE,
            artwork = null, conditionGlyph = null, activityLevel = 0f, reducedMotion = true)
        recording -> signal.copy(state = HaloSignalState.CAPTURING, label = "Recording", glyph = HaloGlyph.RECORD)
        else -> signal
    }
    val targetSize = if (navigation.kids) 56.dp else 48.dp
    val listedActions = actions.filterNot { it.id.endsWith(".back") || it.title == "Back" && it.glyph == HaloGlyph.PREVIOUS }
    // One disclosure owner, two intentionally separate lists.
    var disclosure by rememberSaveable { mutableStateOf<String?>(null) }
    var opener by remember { mutableStateOf("menu") }
    var resumeGuidedHelp by rememberSaveable(navigation.entryId) { mutableStateOf(false) }
    var helpWasBlocked by rememberSaveable(navigation.entryId) { mutableStateOf(false) }
    var guidedActionOwner by remember(navigation.entryId) { mutableStateOf<ShellPageOwner?>(null) }
    var restoreFocus by remember { mutableStateOf<String?>(null) }
    val menuFocus = remember { FocusRequester() }
    val orbFocus = remember { FocusRequester() }
    val actionsFocus = remember { FocusRequester() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val pageProjection = LocalShellPageActions.current?.value?.takeIf { LocalShellPageOwnerFilter.current(it.owner) }
    val pinFamily = pageProjection?.owner?.routeFamily?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) } ?: primaryNavigationRoute(navigation.route)
    val canEditPins = !navigation.kids && pinFamily != null && onActionPins != null && listedActions.isNotEmpty()
    val hasActionDisclosure = listedActions.isNotEmpty() || pageProjection?.owner?.helpDescription != null
    val latestPageProjection by rememberUpdatedState(pageProjection)
    val guideSession = com.halo.app.ui.zone5.LocalZone5GuideSession.current
    val inlineGuidance = navigation.guidanceTargetVisible && !navigation.compact && com.halo.app.ui.theme.rememberVisualPreferences().guideLight
    fun close(restore: Boolean = true) {
        if (restore) { resumeGuidedHelp = false; guidedActionOwner = null; helpWasBlocked = false }
        if (disclosure == "help" && restore) {
            pageProjection?.owner?.nextStep?.let { guideSession?.dismiss(it.id) }
            guideSession?.pause()
        }
        disclosure = null; navigation.onDisclosureChanged(false); restoreFocus = if (restore) opener else null
    }
    val open: (String, String) -> Unit = { list, source ->
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            resumeGuidedHelp = false; guidedActionOwner = null
            opener = source; disclosure = list; navigation.onDisclosureChanged(true)
        }
    }
    val coach = LocalHaloPageGuidance.current
    val gestures = HaloGesture.forRoute(navigation.route)
    val latestOpen by rememberUpdatedState(open)
    val latestCoach by rememberUpdatedState(coach)
    val teachDock = HaloGesture.DOCK_SWIPE in gestures
    fun act(effect: () -> Unit) {
        close(false)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) effect()
    }
    fun holdRing() {
        if (navigation.kids) open("menu", "orb") else act { navigation.onNavigate("mini_halo") }
    }
    if (teachDock) {
        BindHaloGestureAlternative(HaloGesture.DOCK_SWIPE) { open("menu", "orb") }
        BindHaloGestureAlternative(HaloGesture.RING_HOLD, ::holdRing)
    }
    LaunchedEffect(navigation.route, navigation.entryId) { close(false) }
    LaunchedEffect(pageProjection?.owner, resumeGuidedHelp, guideSession?.paused, lifecycleState) {
        val owner = pageProjection?.owner
        if (resumeGuidedHelp && (owner != guidedActionOwner || helpWasBlocked)) {
            if (owner == null || guideSession?.applies(owner) != true) {
                resumeGuidedHelp = false; guidedActionOwner = null
            } else if (owner.guidanceBlocked) {
                helpWasBlocked = true
                if (disclosure == "help") close(false) // Let the real inspector/recovery own input.
            } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                if (disclosure == null) { disclosure = "help"; navigation.onDisclosureChanged(true) }
                resumeGuidedHelp = false; guidedActionOwner = null; helpWasBlocked = false
            }
        }
    }
    LaunchedEffect(restoreFocus) {
        val target = restoreFocus ?: return@LaunchedEffect
        withFrameNanos { }
        when (target) { "orb" -> orbFocus; "actions" -> if (hasActionDisclosure) actionsFocus else menuFocus; else -> menuFocus }.requestFocus()
        restoreFocus = null
    }
    DisposableEffect(lifecycle, navigation.entryId) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) {
            val owner = latestPageProjection?.owner
            if (disclosure == "help" && owner != null && guideSession?.applies(owner) == true) {
                resumeGuidedHelp = true; helpWasBlocked = true
            }
            close(false)
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    androidx.activity.compose.BackHandler(disclosure != null) { close() }
    val menuLabel = if (navigation.zone6) uiText("Places") else stringResource(R.string.zone5_menu)
    val actionsLabel = stringResource(R.string.zone5_all_actions)
    val primary = if (paused.paused) stringResource(R.string.halo_pause_title)
    else if (routeFamily == "private_chat") uiText("Close")
    else if (recording) uiText("Stop recording") else uiText(when (interaction.primary) {
        HaloOrbPrimary.TALK -> "Talk"
        HaloOrbPrimary.FINISH -> "Send voice"
        HaloOrbPrimary.STOP -> "Stop"
        HaloOrbPrimary.INTERRUPT -> "Interrupt reply and talk"
        HaloOrbPrimary.REVIEW -> "Review"
    })
    val caption = if (paused.paused) pausedLabel
    else if (recording) uiText("Recording")
    else if (routeFamily == "private_chat" && signal.state == HaloSignalState.IDLE) uiText("Private")
    else if (routeFamily == "focus" && signal.state == HaloSignalState.IDLE) stringResource(R.string.halo_focus_title)
    else if (navigation.quietToday && signal.state == HaloSignalState.IDLE) "halo"
    else if (navigation.zone6) uiText(when (signal.state) {
        HaloSignalState.LISTENING, HaloSignalState.CAPTURING -> "Listening"
        HaloSignalState.THINKING, HaloSignalState.ACTING -> "Working"
        HaloSignalState.WAITING -> "Waiting for you"
        HaloSignalState.SPEAKING -> "Tap to stop"
        else -> "Talk"
    }) else uiText(when (interaction.primary) {
        HaloOrbPrimary.FINISH -> "Send"
        HaloOrbPrimary.INTERRUPT -> "Talk"
        else -> when (interaction.primary) {
            HaloOrbPrimary.TALK -> "Talk"
            HaloOrbPrimary.STOP -> "Stop"
            else -> "Review"
        }
    })
    val activityDescription = uiText(shownSignal.label)
    val menuAction = if (navigation.zone6) uiText("Open Places") else stringResource(R.string.zone5_open_menu)
    val flatLabels = LocalDensity.current.fontScale >= 1.4f
    val gesture = orbModifier.focusRequester(orbFocus).pointerInput(teachDock) {
        var drag = 0f
        detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragCancel = { drag = 0f },
            onDragEnd = {
                if (drag < -24.dp.toPx()) {
                    latestOpen("menu", "orb")
                    if (teachDock) latestCoach?.gestureUsed(HaloGesture.DOCK_SWIPE)
                }
                drag = 0f
            }) { change, amount ->
            drag += amount
            // Consumption cancels the face's pending click, including a failed/downward menu drag.
            change.consume()
        }
    }.semantics {
        stateDescription = activityDescription
        customActions = listOf(CustomAccessibilityAction(menuAction) { open("menu", "orb"); true })
    }
    val actionListControl: @Composable (Modifier) -> Unit = { modifier ->
        TextButton(onClick = { open("actions", "actions") },
            contentPadding = if (size <= 48.dp) PaddingValues(horizontal = 4.dp, vertical = 8.dp) else ButtonDefaults.TextButtonContentPadding,
            modifier = modifier.focusRequester(actionsFocus).heightIn(min = targetSize)) {
            Text(actionsLabel, textAlign = TextAlign.Center)
        }
    }
    if (navigation.zone6) {
        val interactions = remember { MutableInteractionSource() }
        val reduce = com.halo.app.ui.fx.rememberHaloReducedMotion()
        val visual = com.halo.app.ui.theme.rememberVisualPreferences()
        val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        val chosen = zone6SelectedPlace(navigation.route, navigation.kids)
        var showReceipt by remember(signal.state, signal.label) { mutableStateOf(signal.state == HaloSignalState.SUCCESS) }
        LaunchedEffect(signal.state, signal.label) {
            if (signal.state == HaloSignalState.SUCCESS) {
                kotlinx.coroutines.delay(2400)
                showReceipt = false
            }
        }
        val activeState = !paused.paused && !recording && (showReceipt || signal.state in setOf(HaloSignalState.LISTENING,
            HaloSignalState.CAPTURING, HaloSignalState.THINKING, HaloSignalState.ACTING, HaloSignalState.WAITING,
            HaloSignalState.SPEAKING, HaloSignalState.ERROR, HaloSignalState.OFFLINE))
        LaunchedEffect(signal.state) {
            if (visual.haptics && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) when (signal.state) {
                HaloSignalState.WAITING, HaloSignalState.SUCCESS -> haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                HaloSignalState.ERROR -> haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Reject)
                else -> Unit
            }
        }
        val face: @Composable () -> Unit = {
            Column(gesture.widthIn(min = size, max = if (flatLabels) 148.dp else 94.dp).zone6GuideOutline(Zone6GuideTarget.RING)
                .haloGuideControl("ring").haloGestureAnchor(HaloGesture.RING_HOLD)
                .combinedClickable(interactionSource = interactions, indication = null, role = Role.Button,
                    onClickLabel = primary, onClick = {
                        act {
                            if (visual.haptics) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                            when {
                                paused.paused -> navigation.onNavigate("pause")
                                routeFamily == "private_chat" -> pageProjection?.invoke("page.private_chat.close")
                                recording -> pageProjection?.invoke("page.record.primary")
                                else -> onPrimary(interaction.primary)
                            }
                        }
                    }, onLongClick = {
                        holdRing()
                        if (teachDock) coach?.gestureUsed(HaloGesture.RING_HOLD)
                    })
                .semantics(mergeDescendants = true) { testTag = "zone6-ring"; contentDescription = primary },
                horizontalAlignment = Alignment.CenterHorizontally) {
                HaloSignalFace(shownSignal, Modifier.clearAndSetSemantics { }, size = size, zone5 = true,
                    kids = navigation.kids, interactionSource = interactions, dock = true)
                if (!(navigation.compact && navigation.composer)) Text(caption, style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center, modifier = Modifier.clearAndSetSemantics { })
            }
        }
        if (navigation.compact && navigation.composer) face()
        else BoxWithConstraints(Modifier.fillMaxWidth().semantics { isTraversalGroup = true }) {
            val showDockNew = maxWidth >= 324.dp && !flatLabels
            val labelsAlways = (visual.viewMode == com.halo.app.ui.theme.HaloViewMode.EASY ||
                visual.buttonLabels == com.halo.app.ui.theme.HaloButtonLabels.ALWAYS || flatLabels) &&
                (!flatLabels || maxWidth >= 420.dp || navigation.kids)
            val tab: @Composable (String) -> Unit = { route ->
                val place = navigation.destinations.firstOrNull { it.route == route }
                    ?: com.halo.app.ui.shell.ShellOrbCatalog.workspaceDestinations(navigation.kids).firstOrNull { it.route == route }
                if (place != null) {
                    val (icon, glyph) = zone6PlaceGlyph(route)
                    val activeTool = if (routeFamily == "screen/organizer") "planner" else routeFamily
                    val selected = if (!navigation.kids && route == "home") chosen in setOf("home", "notebook", "screen/sources")
                        else if (navigation.destinations.any { it.route == route }) route == chosen else activeTool == route
                    Zone6Tab(if (route == "study") "Learn" else place.title,
                        glyph, icon, selected = selected, count = when {
                            route == "activity" -> navigation.attentionCount
                            route == "devices" && navigation.devicesNeedAttention -> 1
                            else -> 0
                        },
                        modifier = Modifier.fillMaxWidth().alpha(if (focusActive && !selected) .76f else 1f),
                        onClick = { act { navigation.onNavigate(route) } },
                        showLabel = labelsAlways || selected)
                }
            }
            val places: @Composable () -> Unit = {
                Zone6Tab("Places", HaloGlyph.INTEGRATIONS, "screen_widgets", disclosure == "menu", 0,
                    modifier = Modifier.fillMaxWidth().focusRequester(menuFocus).haloGuideControl("places"),
                    onClick = { if (disclosure == "menu") close() else open("menu", "menu") })
            }
            val openActionMenu: (() -> Unit)? = if (hasActionDisclosure) ({ open("actions", "actions") }) else null
            if (navigation.compact) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (navigation.primaryUtility != null) Zone6UtilityRow(navigation.primaryUtility, onNew = null,
                            onMore = openActionMenu, moreModifier = Modifier.focusRequester(actionsFocus))
                        else Zone6ActionRow(listedActions, pageProjection?.owner?.routeFamily, true, navigation.kids,
                            disclosure == "actions", onMore = { if (disclosure == "actions") close() else open("actions", "actions") },
                            onAction = { action -> act { onAction(action) } }, moreModifier = Modifier.focusRequester(actionsFocus),
                            reducedMotion = reduce || signal.reducedMotion)
                    }
                    face()
                    Box(Modifier.widthIn(min = 48.dp, max = 108.dp)) { places() }
                }
                return@BoxWithConstraints
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (activeState) Zone6LiveIsland(signal, interaction, reduce || signal.reducedMotion,
                    onPrimary = { act { onPrimary(interaction.primary) } }, onStop = { act(onStop) },
                    more = if (listedActions.isNotEmpty() && signal.state in setOf(HaloSignalState.ERROR, HaloSignalState.OFFLINE))
                        ({ open("actions", "orb") }) else null)
                else if (navigation.primaryUtility != null) {
                    val newNote = listedActions.firstOrNull { it.id == "page:page.notebook.new-note" && it.enabled }
                        ?.takeIf { showDockNew }
                    Zone6UtilityRow(navigation.primaryUtility,
                        onNew = newNote?.let { action -> { act { onAction(action) } } }, onMore = openActionMenu,
                        moreModifier = Modifier.focusRequester(actionsFocus))
                }
                else if (!navigation.composer && !navigation.quietToday) {
                    Zone6ActionRow(listedActions, pageProjection?.owner?.routeFamily, navigation.compact, navigation.kids,
                        disclosure == "actions", onMore = { if (disclosure == "actions") close() else open("actions", "actions") },
                        onAction = { action -> act { onAction(action) } }, moreModifier = Modifier.focusRequester(actionsFocus),
                        reducedMotion = reduce || signal.reducedMotion)
                } else if (hasActionDisclosure && !navigation.compact && listedActions.size > 1 && disclosure == "actions") {
                    actionListControl(Modifier.align(Alignment.End))
                }
                Row(Modifier.fillMaxWidth().zone6GuideOutline(Zone6GuideTarget.NAVIGATION), horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { tab("home") }
                    if (!navigation.kids) Box(Modifier.weight(1f)) { tab("search") }
                    Box(contentAlignment = Alignment.Center) { face() }
                    if (navigation.kids) {
                        Box(Modifier.weight(1f)) {
                            Zone6Tab("Grown-up space", HaloGlyph.SECURITY, "action_lock", false, 0,
                                modifier = Modifier.fillMaxWidth(), onClick = { act { navigation.onNavigate("settings") } })
                        }
                    } else {
                        Box(Modifier.weight(1f)) { tab("devices") }
                        Box(Modifier.weight(1f)) { tab("settings") }
                    }
                }
            }
        }
    } else if (navigation.compact || navigation.inlineControls) {
        val interactions = remember { MutableInteractionSource() }
        val orbAndCaption: @Composable () -> Unit = {
            Column(gesture.widthIn(min = if (navigation.compact) targetSize else size, max = if (flatLabels) 180.dp else 112.dp)
                .combinedClickable(interactionSource = interactions, indication = null, role = Role.Button,
                    onClickLabel = primary, onClick = { act { onPrimary(interaction.primary) } },
                    onLongClick = ::holdRing)
                .semantics(mergeDescendants = true) { testTag = "zone5-orb-action"; contentDescription = primary },
                horizontalAlignment = Alignment.CenterHorizontally) {
                HaloSignalFace(signal, Modifier.clearAndSetSemantics { }, size = if (navigation.compact) targetSize else size,
                    zone5 = true, kids = navigation.kids, interactionSource = interactions)
                Text(caption, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                    modifier = Modifier.clearAndSetSemantics { })
                if (signal.state != HaloSignalState.IDLE) Text(activityDescription, style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center, modifier = Modifier.clearAndSetSemantics { })
            }
        }
        val menuControl: @Composable (Modifier) -> Unit = { modifier ->
            TextButton(onClick = { open("menu", "menu") }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = modifier.focusRequester(menuFocus).heightIn(min = targetSize)) {
                BadgedBox(badge = { if (navigation.hasUpdates) Badge() }) {
                    if (!flatLabels) Zone5Icon("action_up", HaloGlyph.NEXT, Modifier.size(24.dp))
                    else Text(menuLabel)
                }
                if (!flatLabels) { Spacer(Modifier.width(6.dp)); Text(menuLabel) }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().semantics { isTraversalGroup = true }) {
            if (flatLabels && maxWidth < 480.dp) {
                // Accessible fallback: reflow complete labels instead of splitting words into narrow columns.
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (hasActionDisclosure) actionListControl(Modifier.weight(1f))
                        else Spacer(Modifier.weight(1f))
                        menuControl(Modifier.weight(1f))
                    }
                    orbAndCaption()
                }
            } else Row(Modifier.fillMaxWidth().heightIn(min = targetSize),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (hasActionDisclosure) actionListControl(Modifier)
                }
                orbAndCaption()
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { menuControl(Modifier) }
            }
        }
    } else {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    if (flatLabels && hasActionDisclosure) {
        actionListControl(Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
    if (flatLabels) Box(Modifier.fillMaxWidth()) { navigation.profileStatus() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            if (!flatLabels && listedActions.isNotEmpty()) actionListControl(Modifier)
            if (!flatLabels) navigation.profileStatus()
        }
        Column(Modifier.width(if (size <= 48.dp) 64.dp else 112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            HaloSignalFace(signal, gesture, size = size, contentDescription = primary,
                onClick = { act { onPrimary(interaction.primary) } }, onClickLabel = primary,
                onLongClick = ::holdRing, zone5 = true, kids = navigation.kids)
            if (size > 48.dp) {
            Spacer(Modifier.height(4.dp))
            Text(caption,
                style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.BottomEnd) {
            TextButton(onClick = { open("menu", "menu") },
                modifier = Modifier.focusRequester(menuFocus).heightIn(min = targetSize)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BadgedBox(badge = { if (navigation.hasUpdates) Badge() }) {
                        Zone5Icon("action_up", HaloGlyph.NEXT, Modifier.size(24.dp))
                    }
                    Text(menuLabel)
                }
            }
        }
    }
    if (size <= 48.dp) Text(caption, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
    }
    val sheetContent: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!navigation.zone6) Text(when (disclosure) {
                "menu" -> stringResource(R.string.zone5_navigation)
                "help" -> stringResource(R.string.zone5_help_title)
                "pins" -> stringResource(R.string.zone5_pins_choose)
                else -> actionsLabel
            }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (navigation.zone6 && coach != null && disclosure in setOf("menu", "actions", "help")) {
                val guideTitle = stringResource(if (rememberHaloTouchExploration()) R.string.coach_explore else R.string.coach_show)
                if (disclosure == "actions" || disclosure == "menu" && navigation.composer) {
                    pageProjection.editPageAction()?.takeIf { it.enabled }?.let { editor ->
                        TextButton(onClick = { act { pageProjection?.invoke(editor.id) } },
                            modifier = Modifier.fillMaxWidth().heightIn(min = targetSize)) {
                            Text(stringResource(R.string.coach_edit_page), Modifier.weight(1f))
                        }
                    }
                }
                TextButton(onClick = { act(coach::showControls) }, modifier = Modifier.fillMaxWidth().heightIn(min = targetSize)) {
                    Text(guideTitle, Modifier.weight(1f))
                }
                if (gestures.isNotEmpty()) TextButton(onClick = { act(coach::showGestures) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = targetSize)) {
                    Text(stringResource(R.string.coach_watch), Modifier.weight(1f))
                }
            }
            if (disclosure == "menu") {
                if (navigation.zone6) com.halo.app.ui.zone6.Zone6PlacesLayout(navigation.destinations,
                    zone6SelectedPlace(navigation.route, navigation.kids), navigation.attentionCount, navigation.kids) {
                    act { navigation.onNavigate(it) }
                } else {
                val selected = when {
                    navigation.destinations.any { it.route == "study" } &&
                        (navigation.route?.startsWith("study") == true || navigation.route?.startsWith("notebook/study/") == true) -> "study"
                    navigation.route == "screen" -> "screen"
                    navigation.route?.startsWith("notifications") == true -> "activity"
                    else -> primaryNavigationRoute(navigation.route)
                }
                navigation.destinations.forEach { destination ->
                    TextButton(onClick = { act { navigation.onNavigate(destination.route) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = targetSize).semantics { this.selected = destination.route == selected }) {
                        Zone5Icon(when (destination.route) {
                            "screen" -> "nav_screen"; "home" -> "nav_home"; "notebook" -> "nav_workspace"
                            "devices" -> "nav_devices"; "activity" -> "nav_activity"; "study" -> "tool_study"; else -> "nav_settings"
                        }, destination.glyph, Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (destination.route == "activity") stringResource(R.string.zone5_activities) else uiText(destination.title))
                            when (destination.route) {
                                "home" -> Text(stringResource(R.string.zone5_home_purpose), style = MaterialTheme.typography.bodyMedium)
                                "screen" -> Text(stringResource(R.string.zone5_screen_purpose), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (destination.route == selected) Zone5Icon("action_check", HaloGlyph.SUCCESS, Modifier.size(20.dp))
                    }
                    if (destination.route == "notebook" && !navigation.kids) {
                        var instrumentsExpanded by remember(disclosure) { mutableStateOf(false) }
                        val expansionState = stringResource(if (instrumentsExpanded) R.string.zone5_expanded else R.string.zone5_collapsed)
                        TextButton(onClick = { instrumentsExpanded = !instrumentsExpanded },
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp).heightIn(min = targetSize)
                                .semantics { stateDescription = expansionState }) {
                            Text(stringResource(R.string.zone5_workspace_group), Modifier.weight(1f))
                            Zone5Icon(if (instrumentsExpanded) "action_up" else "action_down", HaloGlyph.NEXT, Modifier.size(24.dp))
                        }
                        val shelf = com.halo.app.ui.zone6.rememberZone6PageSetting(com.halo.app.ui.toolkit.HaloToolkit.SHELF)
                        if (instrumentsExpanded) com.halo.app.ui.toolkit.HaloToolkit.pinned(shelf.value).forEach { instrument ->
                            val (icon, glyph) = com.halo.app.ui.zone6.zone6PlaceGlyph(instrument.route)
                            TextButton(onClick = { act { navigation.onNavigate(instrument.route) } },
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp).heightIn(min = targetSize)) {
                                Zone5Icon(icon, glyph, Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(instrument.title), Modifier.weight(1f))
                            }
                        }
                    }
                }
                }
                if (navigation.zone6 && (navigation.composer || navigation.quietToday) && listedActions.isNotEmpty()) {
                    TextButton(onClick = { disclosure = "actions" }, modifier = Modifier.heightIn(min = targetSize)) {
                        Text(if (navigation.composer) uiText("Chat actions") else actionsLabel)
                    }
                }
                HorizontalDivider()
                navigation.profileControl()
            } else if (disclosure == "help") {
                pageProjection?.let { projection -> com.halo.app.ui.zone5.Zone5TaskHelp(projection, navigation.kids,
                    onAction = { id ->
                        if (!inlineGuidance && guideSession?.applies(projection.owner) == true && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            guidedActionOwner = projection.owner
                            helpWasBlocked = false
                            resumeGuidedHelp = true
                            if (!projection.invoke(id)) { resumeGuidedHelp = false; guidedActionOwner = null }
                        } else act { projection.invoke(id) }
                    }, onShowControl = { if (inlineGuidance) close(false) }) }
            } else if (disclosure == "pins" && canEditPins) {
                com.halo.app.ui.zone5.Zone5PinnedActionsEditor(requireNotNull(pinFamily), actionPins, listedActions,
                    requireNotNull(onActionPins), onDone = { disclosure = "actions" })
            } else {
                navigation.compactUtility()
                LocalShellPageActions.current?.value?.takeIf { LocalShellPageOwnerFilter.current(it.owner) }
                    ?.owner?.statusLabel?.takeUnless { navigation.compactUtilityIncludesStatus }?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (listedActions.isEmpty()) Text(stringResource(R.string.zone5_no_actions))
                val owner = LocalShellPageActions.current?.value?.takeIf { LocalShellPageOwnerFilter.current(it.owner) }?.owner
                if (canEditPins) {
                    val pins = actionPins.families[requireNotNull(pinFamily)].orEmpty().mapNotNull { id -> listedActions.find { it.id == id } }
                    if (pins.isNotEmpty()) {
                        Text(stringResource(R.string.zone5_pins_title), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() })
                        pins.forEach { action ->
                            TextButton(onClick = { if (action.enabled) act { onAction(action) } }, enabled = action.enabled,
                                modifier = Modifier.fillMaxWidth().heightIn(min = targetSize)) {
                                Zone5Icon(zone5ActionIconId(action), action.glyph, Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp)); Text(uiText(action.title), Modifier.weight(1f))
                            }
                            if (!action.enabled) Text(action.disabledReason?.let { uiText(it) } ?: stringResource(R.string.zone5_unavailable))
                        }
                        HorizontalDivider()
                    }
                    TextButton(onClick = { disclosure = "pins" }, modifier = Modifier.fillMaxWidth().heightIn(min = targetSize)) {
                        Text(stringResource(R.string.zone5_pins_choose))
                    }
                }
                var query by remember(disclosure, navigation.entryId, owner?.objectId) { mutableStateOf("") }
                if (listedActions.size > 8) OutlinedTextField(query, { query = it },
                    label = { Text(stringResource(R.string.zone5_find_action)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                val matching = listedActions.filter { com.halo.app.ui.zone5.actionMatchesQuery(it, query) }
                val grouped = if (owner?.routeFamily in workspaceMenuFamilies)
                    WorkspaceMenuGroup.entries.map { group -> stringResource(group.label) to matching.filter {
                        it.id.startsWith("page:") && workspaceMenuGroup(it.id) == group
                    } }.filter { it.second.isNotEmpty() } +
                        listOf(stringResource(R.string.zone5_group_task) to matching.filterNot { it.id.startsWith("page:") }).filter { it.second.isNotEmpty() }
                else matching.groupBy { com.halo.app.ui.zone5.localActionGroup(owner?.routeFamily ?: primaryNavigationRoute(navigation.route), it.id) }
                    .entries.sortedBy { it.key.ordinal }.map { stringResource(it.key.label) to it.value }
                if (matching.isEmpty() && query.isNotBlank()) Text(stringResource(R.string.zone5_no_matching_action))
                grouped.forEach { (title, entries) ->
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    entries.forEach { action ->
                        Column {
                            TextButton(onClick = { if (action.enabled) act { onAction(action) } }, enabled = action.enabled,
                                modifier = Modifier.fillMaxWidth().heightIn(min = targetSize)) {
                                Zone5Icon(zone5ActionIconId(action), action.glyph, Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp)); Text(uiText(action.title), Modifier.weight(1f))
                            }
                            if (!action.enabled) Text(action.disabledReason?.let { uiText(it) }
                                ?: stringResource(R.string.zone5_unavailable), style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                        }
                    }
                }
                if (owner?.helpDescription != null) {
                    HorizontalDivider()
                    TextButton(onClick = { disclosure = "help" }, modifier = Modifier.fillMaxWidth().heightIn(min = targetSize)) {
                        Text(stringResource(R.string.zone5_help_title))
                    }
                    owner.nextStep?.let { step -> TextButton(onClick = { guideSession?.dismiss(step.id) },
                        modifier = Modifier.heightIn(min = targetSize)) { Text(stringResource(R.string.zone5_help_dismiss)) } }
                }
            }
            if (interaction.canStop) OutlinedButton(onClick = { act(onStop) }, modifier = Modifier.fillMaxWidth().heightIn(min = targetSize)) { Text(uiText("Stop")) }
            if (!navigation.zone6) TextButton(onClick = { close() }, modifier = Modifier.heightIn(min = targetSize)) { Text(uiText("Close")) }
        }
    }
    if (disclosure != null) {
        if (navigation.zone6) Zone6ButtonSheet(when (disclosure) {
            "menu" -> menuLabel
            "help" -> stringResource(R.string.zone5_help_title)
            "pins" -> stringResource(R.string.zone5_pins_choose)
            else -> uiText("More")
        }, onDismiss = { close() }, content = sheetContent)
        else LocalizedMaterialBottomSheet(onDismissRequest = { close() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), content = sheetContent)
    }
}
