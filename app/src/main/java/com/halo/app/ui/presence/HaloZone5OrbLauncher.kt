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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val familyControl: @Composable () -> Unit = {},
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
    val latestOpen by rememberUpdatedState(open)
    fun act(effect: () -> Unit) {
        close(false)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) effect()
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
    val menuLabel = stringResource(R.string.zone5_menu)
    val actionsLabel = stringResource(R.string.zone5_all_actions)
    val primary = uiText(when (interaction.primary) {
        HaloOrbPrimary.TALK -> "Talk"
        HaloOrbPrimary.FINISH -> "Send voice"
        HaloOrbPrimary.STOP -> "Stop"
        HaloOrbPrimary.INTERRUPT -> "Interrupt reply and talk"
        HaloOrbPrimary.REVIEW -> "Review"
    })
    val caption = uiText(when (interaction.primary) {
        HaloOrbPrimary.FINISH -> "Send"
        HaloOrbPrimary.INTERRUPT -> "Talk"
        else -> when (interaction.primary) {
            HaloOrbPrimary.TALK -> "Talk"
            HaloOrbPrimary.STOP -> "Stop"
            else -> "Review"
        }
    })
    val activityDescription = uiText(signal.label)
    val menuAction = stringResource(R.string.zone5_open_menu)
    val flatLabels = LocalDensity.current.fontScale >= 1.4f
    val gesture = orbModifier.focusRequester(orbFocus).pointerInput(Unit) {
        var drag = 0f
        detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragCancel = { drag = 0f },
            onDragEnd = { if (drag < -24.dp.toPx()) latestOpen("menu", "orb"); drag = 0f }) { change, amount ->
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
    if (navigation.compact || navigation.inlineControls) {
        val interactions = remember { MutableInteractionSource() }
        val orbAndCaption: @Composable () -> Unit = {
            Column(gesture.widthIn(min = if (navigation.compact) targetSize else size, max = if (flatLabels) 180.dp else 112.dp)
                .combinedClickable(interactionSource = interactions, indication = null, role = Role.Button,
                    onClickLabel = primary, onClick = { act { onPrimary(interaction.primary) } },
                    onLongClick = { open("menu", "orb") })
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
                onLongClick = { open("menu", "orb") }, zone5 = true, kids = navigation.kids)
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
    if (disclosure != null) LocalizedMaterialBottomSheet(onDismissRequest = { close() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when (disclosure) { "menu" -> stringResource(R.string.zone5_navigation); "help" -> stringResource(R.string.zone5_help_title); "pins" -> stringResource(R.string.zone5_pins_choose); else -> actionsLabel },
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (disclosure == "menu") {
                navigation.familyControl()
                val selected = when {
                    navigation.kids && navigation.route?.substringBefore("?") in setOf("screen/organizer", "screen/clocks", "screen/brief", "screen/source") -> "screen/organizer"
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
                            "devices" -> "nav_devices"; "activity" -> "nav_activity"; "study" -> "tool_study"
                            "screen/organizer" -> "tool_organizer"
                            else -> "nav_settings"
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
                        if (instrumentsExpanded) com.halo.app.ui.shell.ShellOrbCatalog.workspaceDestinations(navigation.kids).forEach { instrument ->
                            val icon = when (instrument.route) {
                                "screen/organizer" -> "tool_organizer"
                                "studio" -> "tool_studio"; "study" -> "tool_study"; "notebook/memory" -> "tool_memory"
                                "canvases" -> "tool_canvas"; else -> "tool_notes"
                            }
                            TextButton(onClick = { act { navigation.onNavigate(instrument.route) } },
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp).heightIn(min = targetSize)) {
                                Zone5Icon(icon, instrument.glyph, Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(com.halo.app.ui.zone5.workspaceInstrumentName(icon, uiText(instrument.title)), Modifier.weight(1f))
                            }
                        }
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
            TextButton(onClick = { close() }, modifier = Modifier.heightIn(min = targetSize)) { Text(uiText("Close")) }
        }
    }
}
