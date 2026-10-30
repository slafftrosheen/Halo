package com.halo.app.ui.presence

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** Disclosure only: callers retain voice, navigation, permission and execution ownership. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HaloOrbLauncher(signal: HaloSignalPresentation, interaction: HaloOrbInteraction,
    shortcuts: Set<HaloOrbShortcut>, onShortcuts: (Set<HaloOrbShortcut>) -> Unit,
    onPrimary: (HaloOrbPrimary) -> Unit, onStop: () -> Unit,
    onTools: (HaloToolCatalogRequest) -> Unit, size: Dp = 64.dp,
    face: (@Composable (onClick: () -> Unit, label: String) -> Unit)? = null,
    orbModifier: Modifier = Modifier,
    destinations: List<HaloOrbDestination> = emptyList(), onNavigate: (String) -> Unit = {},
    contextActions: List<HaloOrbContextAction> = emptyList(),
    onContextAction: (HaloOrbContextAction) -> Unit = {},
    hint: String? = null, onDismissHint: () -> Unit = {}, leading: (@Composable () -> Unit)? = null,
    showToolCatalog: Boolean = true) {
    LocalHaloZone5Navigation.current?.let { navigation ->
        HaloZone5OrbLauncher(signal, interaction, onPrimary, onStop, contextActions, onContextAction,
            size, orbModifier, navigation)
        return
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var satellites by rememberSaveable { mutableStateOf(false) }
    var customizing by remember { mutableStateOf(false) }
    var disclosedHint by rememberSaveable { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val userReducedMotion by com.halo.app.ui.theme.rememberReducedMotion()
    // Thermal/battery motion limits belong to the face, not to menu placement.
    // Heating a device must not move an already-open inline chooser into a modal.
    val reduced = userReducedMotion || signal.reducedMotion
    val context = LocalContext.current
    val accessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager }
    var touchExploration by remember(accessibility) { mutableStateOf(accessibility?.isTouchExplorationEnabled == true) }
    DisposableEffect(accessibility, lifecycle) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { expanded = false; satellites = false; customizing = false }
        }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            accessibility?.removeTouchExplorationStateChangeListener(listener)
            lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(interaction.primary) { expanded = false; satellites = false; customizing = false }
    val configuration = LocalConfiguration.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val zoneOwned = LocalShellUtilityBand.current != null
    val inline = !zoneOwned && configuration.screenWidthDp >= 600 && configuration.screenHeightDp >= 600 &&
        density.fontScale < 1.4f && !reduced && !touchExploration
    val fanAllowed = !zoneOwned && configuration.screenWidthDp >= 360 && configuration.screenHeightDp >= 600 &&
        density.fontScale < 1.4f && !reduced && !touchExploration && !keyboardVisible && destinations.isNotEmpty()
    LaunchedEffect(keyboardVisible) { if (keyboardVisible) satellites = false }
    LaunchedEffect(fanAllowed) { if (satellites && !fanAllowed) { satellites = false; expanded = true } }
    androidx.activity.compose.BackHandler(satellites) { satellites = false }
    fun close() { expanded = false; satellites = false; customizing = false; disclosedHint = null }
    androidx.activity.compose.BackHandler(expanded && inline) { close() }
    fun menu(full: Boolean) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        disclosedHint = hint
        onDismissHint()
        customizing = false
        if (!full && fanAllowed) { satellites = !satellites; expanded = false }
        else { expanded = !expanded; satellites = false }
    }
    fun act(action: () -> Unit) {
        close()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) action()
    }
    val primaryLabel = uiText(when (interaction.primary) {
        HaloOrbPrimary.TALK -> "Talk"
        HaloOrbPrimary.FINISH -> "Send voice"
        HaloOrbPrimary.STOP -> "Stop"
        HaloOrbPrimary.INTERRUPT -> "Interrupt reply and talk"
        HaloOrbPrimary.REVIEW -> "Review"
    })
    val disclosureLabel = stringResource(R.string.halo_orb_actions)
    val orbLabel = primaryLabel
    val orbClick: () -> Unit = {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            act { onPrimary(interaction.primary) }
        }
    }
    val contents: @Composable (Boolean) -> Unit = { stopPinned ->
        if (customizing) {
            Text(stringResource(R.string.halo_orb_customize), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.halo_orb_shortcuts_hint), style = MaterialTheme.typography.bodyMedium)
            HaloOrbShortcut.entries.forEach { shortcut ->
                Row(Modifier.fillMaxWidth().toggleable(value = shortcut in shortcuts, role = Role.Checkbox, onValueChange = { checked ->
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                            onShortcuts(if (checked) shortcuts + shortcut else shortcuts - shortcut)
                    }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = shortcut in shortcuts, onCheckedChange = null, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
                    Text(stringResource(shortcut.label), Modifier.weight(1f))
                }
            }
            TextButton(onClick = { customizing = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Done")) }
        } else {
            Text(disclosureLabel, style = MaterialTheme.typography.titleMedium)
            disclosedHint?.let { Text(uiText(it), style = MaterialTheme.typography.bodySmall) }
            Text(uiText(signal.label), style = MaterialTheme.typography.bodyMedium)
            contextActions.forEach { action ->
                OutlinedButton(onClick = { act { onContextAction(action) } }, enabled = action.enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    HaloIcon(action.glyph, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text(uiText(action.title))
                }
            }
            destinations.forEach { destination ->
                TextButton(onClick = { act { onNavigate(destination.route) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    HaloIcon(destination.glyph, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp)); Text(uiText(destination.title), Modifier.weight(1f))
                }
            }
            if (!stopPinned || interaction.primary != HaloOrbPrimary.STOP) {
                Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), onClick = { act { onPrimary(interaction.primary) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(primaryLabel)
                }
            }
            if (!stopPinned && interaction.canStop && interaction.primary != HaloOrbPrimary.STOP)
                OutlinedButton(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), onClick = { act(onStop) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Stop")) }
            if (showToolCatalog) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HaloOrbShortcut.entries.filter { it in shortcuts }.forEach { shortcut ->
                    SuggestionChip(onClick = { act { onTools(HaloToolCatalogRequest(shortcut.group, shortcut.entry)) } },
                        label = { Text(stringResource(shortcut.label)) },
                        icon = { HaloIcon(shortcut.icon, null, Modifier.size(20.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            if (showToolCatalog) OutlinedButton(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), onClick = { act { onTools(HaloToolCatalogRequest(com.halo.app.ui.screens.home.HomeToolGroup.CREATE)) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.halo_orb_all_tools)) }
            if (showToolCatalog) TextButton(onClick = { customizing = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.halo_orb_customize)) }
        }
        TextButton(onClick = ::close, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Close")) }
    }
    val showShortcuts = stringResource(R.string.halo_orb_show_shortcuts)
    val fullMenu = uiText("All destinations and actions")
    val longPress: () -> Unit = { menu(false) }
    val openMenu: () -> Unit = { menu(true) }
    val currentOpenMenu by rememberUpdatedState(openMenu)
    var drag by remember { mutableFloatStateOf(0f) }
    val gestures = orbModifier.pointerInput(Unit) {
        detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragCancel = { drag = 0f },
            onDragEnd = { if (drag < -32.dp.toPx()) currentOpenMenu(); drag = 0f }) { change, amount ->
                drag += amount; change.consume()
            }
    }.semantics { customActions = listOf(CustomAccessibilityAction(fullMenu) { openMenu(); true }) }
    val wideControl = zoneOwned || leading != null || destinations.isNotEmpty() || contextActions.isNotEmpty()
    val controlWidth = if (wideControl) Modifier.fillMaxWidth() else Modifier.width(size + 48.dp)
    val drawFace: @Composable () -> Unit = {
        if (face != null) face(orbClick, orbLabel)
        else HaloSignalFace(signal, modifier = gestures, size = size, contentDescription = orbLabel,
            onClick = orbClick, onClickLabel = orbLabel, onLongClick = longPress)
    }
    val drawMenu: @Composable (Modifier) -> Unit = { modifier ->
        IconButton(onClick = openMenu, modifier = modifier.size(48.dp)) {
            HaloIcon(HaloGlyph.TOOL, showShortcuts)
        }
    }
    Column(Modifier.widthIn(max = 360.dp).then(controlWidth).padding(horizontal = if (wideControl && !zoneOwned) 12.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(satellites, enter = fadeIn(tween(com.halo.app.ui.design.HaloMotion.disclosureEnterMs)) + scaleIn(initialScale = .94f),
            exit = fadeOut(tween(com.halo.app.ui.design.HaloMotion.disclosureExitMs)) + scaleOut(targetScale = .94f)) {
            Box(Modifier.fillMaxWidth().height(164.dp)) {
                // A bounded upward fan, with labels and full-size targets; never orbiting.
                val first = destinations.filter { it.route in setOf("home", "notebook", "activity", "devices", "settings", "study") }
                    .take(if (contextActions.isEmpty()) 5 else 3)
                val satelliteActions = contextActions.filter { it.enabled }.take(2)
                val total = (first.size + satelliteActions.size).coerceAtLeast(1)
                val entries: List<Triple<String, HaloGlyph, () -> Unit>> = first.map { d -> Triple(d.title, d.glyph) { act { onNavigate(d.route) } } } +
                    satelliteActions.map { a -> Triple(a.title, a.glyph) { act { onContextAction(a) } } }
                entries.forEachIndexed { index, (title, glyph, action) ->
                    val position = index - (total - 1) / 2f
                    val height = 80f - kotlin.math.abs(position) * 32f
                    FilledTonalButton(onClick = action, modifier = Modifier.align(Alignment.BottomCenter)
                        .offset(x = (position * 64f).dp, y = (-height).dp)
                        .width(62.dp).heightIn(min = 72.dp), contentPadding = PaddingValues(4.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            HaloIcon(glyph, null, Modifier.size(24.dp))
                            Text(uiText(title), style = MaterialTheme.typography.labelSmall, maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (hint != null && configuration.screenHeightDp >= 600 && density.fontScale < 1.4f && !keyboardVisible) {
            TextButton(onClick = { menu(true) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(uiText(hint), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (zoneOwned && LocalShellOrbitalLayout.current) Box(Modifier.fillMaxWidth().height(184.dp)) {
            Box(Modifier.align(Alignment.TopCenter).offset(y = 68.dp)) { drawFace() }
            drawMenu(Modifier.align(Alignment.BottomEnd))
        } else if (zoneOwned) BoxWithConstraints(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                if (this@BoxWithConstraints.maxWidth >= size + 96.dp) Spacer(Modifier.width(48.dp))
                drawFace()
                drawMenu(Modifier)
            }
        } else if (wideControl) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (leading != null) Box(Modifier.align(Alignment.CenterStart).widthIn(max = 112.dp)) { leading() }
            drawFace()
            drawMenu(Modifier.align(Alignment.CenterEnd))
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            drawFace(); drawMenu(Modifier)
        }
        if (expanded && inline) Surface(shape = com.halo.app.ui.design.HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.width(320.dp).heightIn(max = 440.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) { contents(false) }
        }
    }
    if (expanded && !inline) LocalizedMaterialBottomSheet(onDismissRequest = ::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        CompositionLocalProvider(LocalDensity provides density) {
            Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (interaction.canStop) OutlinedButton(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    onClick = { act(onStop) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Stop")) }
                Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) { contents(interaction.canStop) }
            }
        }
    }
}
