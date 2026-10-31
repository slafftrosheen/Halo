package com.halo.app.ui.presence

import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.shell.primaryNavigationRoute
import com.halo.app.ui.zone5.Zone5Icon
import com.halo.app.ui.zone5.zone5ActionIconId

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
)
internal val LocalHaloZone5Navigation = staticCompositionLocalOf<HaloZone5Navigation?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HaloZone5OrbLauncher(
    signal: HaloSignalPresentation, interaction: HaloOrbInteraction,
    onPrimary: (HaloOrbPrimary) -> Unit, onStop: () -> Unit,
    actions: List<HaloOrbContextAction>, onAction: (HaloOrbContextAction) -> Unit,
    size: Dp, orbModifier: Modifier, navigation: HaloZone5Navigation,
) {
    val targetSize = if (navigation.kids) 56.dp else 48.dp
    val listedActions = actions.filterNot { it.id.endsWith(".back") || it.title == "Back" && it.glyph == HaloGlyph.PREVIOUS }
    // One disclosure owner, two intentionally separate lists.
    var disclosure by rememberSaveable { mutableStateOf<String?>(null) }
    var opener by remember { mutableStateOf("menu") }
    var restoreFocus by remember { mutableStateOf<String?>(null) }
    val menuFocus = remember { FocusRequester() }
    val orbFocus = remember { FocusRequester() }
    val actionsFocus = remember { FocusRequester() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun close(restore: Boolean = true) { disclosure = null; navigation.onDisclosureChanged(false); restoreFocus = if (restore) opener else null }
    val open: (String, String) -> Unit = { list, source ->
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { opener = source; disclosure = list; navigation.onDisclosureChanged(true) }
    }
    val latestOpen by rememberUpdatedState(open)
    fun act(effect: () -> Unit) {
        close(false)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) effect()
    }
    LaunchedEffect(navigation.route, navigation.entryId) { close(false) }
    LaunchedEffect(restoreFocus) {
        val target = restoreFocus ?: return@LaunchedEffect
        withFrameNanos { }
        when (target) { "orb" -> orbFocus; "actions" -> if (listedActions.isNotEmpty()) actionsFocus else menuFocus; else -> menuFocus }.requestFocus()
        restoreFocus = null
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) close(false) }
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
    }.semantics { customActions = listOf(CustomAccessibilityAction(menuAction) { open("menu", "orb"); true }) }
    val actionListControl: @Composable (Modifier) -> Unit = { modifier ->
        TextButton(onClick = { open("actions", "actions") },
            contentPadding = if (size <= 48.dp) PaddingValues(horizontal = 4.dp, vertical = 8.dp) else ButtonDefaults.TextButtonContentPadding,
            modifier = modifier.focusRequester(actionsFocus).heightIn(min = targetSize)) {
            Text(actionsLabel, textAlign = TextAlign.Center)
        }
    }
    if (navigation.compact || navigation.inlineControls) {
        Row(Modifier.fillMaxWidth().heightIn(min = targetSize).semantics { isTraversalGroup = true },
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (listedActions.isNotEmpty()) actionListControl(Modifier)
            }
            HaloSignalFace(signal, gesture, size = if (navigation.compact) targetSize else size, contentDescription = primary,
                onClick = { act { onPrimary(interaction.primary) } }, onClickLabel = primary,
                onLongClick = { open("menu", "orb") }, zone5 = true, kids = navigation.kids)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { open("menu", "menu") }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.focusRequester(menuFocus).heightIn(min = targetSize)) {
                    BadgedBox(badge = { if (navigation.hasUpdates) Badge() }) {
                        Zone5Icon("action_up", HaloGlyph.NEXT, Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(6.dp)); Text(menuLabel)
                }
            }
        }
    } else {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    if (flatLabels && listedActions.isNotEmpty()) {
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
            Text(if (interaction.primary == HaloOrbPrimary.TALK) stringResource(R.string.zone5_tap_to_talk) else primary,
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
    if (size <= 48.dp) Text(primary, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
    }
    if (disclosure != null) LocalizedMaterialBottomSheet(onDismissRequest = { close() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (disclosure == "menu") stringResource(R.string.zone5_navigation) else actionsLabel,
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (disclosure == "menu") {
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
                        Text(if (destination.route == "activity") stringResource(R.string.zone5_activities) else uiText(destination.title), Modifier.weight(1f))
                        if (destination.route == selected) Zone5Icon("action_check", HaloGlyph.SUCCESS, Modifier.size(20.dp))
                    }
                }
                HorizontalDivider()
                navigation.profileControl()
            } else {
                navigation.compactUtility()
                LocalShellPageActions.current?.value?.takeIf { LocalShellPageOwnerFilter.current(it.owner) }
                    ?.owner?.statusLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (listedActions.isEmpty()) Text(stringResource(R.string.zone5_no_actions))
                listedActions.forEach { action ->
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
            if (interaction.canStop) OutlinedButton(onClick = { act(onStop) }, modifier = Modifier.fillMaxWidth().heightIn(min = targetSize)) { Text(uiText("Stop")) }
            TextButton(onClick = { close() }, modifier = Modifier.heightIn(min = targetSize)) { Text(uiText("Close")) }
        }
    }
}
