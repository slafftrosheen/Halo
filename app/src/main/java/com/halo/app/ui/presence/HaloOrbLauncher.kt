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
    orbModifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var customizing by remember { mutableStateOf(false) }
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
            if (event == Lifecycle.Event.ON_STOP) { expanded = false; customizing = false }
        }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            accessibility?.removeTouchExplorationStateChangeListener(listener)
            lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(interaction.primary) { expanded = false; customizing = false }
    val configuration = LocalConfiguration.current
    val inline = configuration.screenWidthDp >= 600 && configuration.screenHeightDp >= 600 &&
        density.fontScale < 1.4f && !reduced && !touchExploration
    fun close() { expanded = false; customizing = false }
    fun act(action: () -> Unit) {
        close()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) action()
    }
    val primaryLabel = uiText(when (interaction.primary) {
        HaloOrbPrimary.TALK -> "Talk"
        HaloOrbPrimary.STOP -> "Stop"
        HaloOrbPrimary.INTERRUPT -> "Interrupt reply and talk"
        HaloOrbPrimary.REVIEW -> "Review"
    })
    val disclosureLabel = stringResource(R.string.halo_orb_actions)
    val orbLabel = if (interaction.primary == HaloOrbPrimary.TALK) disclosureLabel else primaryLabel
    val orbClick: () -> Unit = {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            if (interaction.primary == HaloOrbPrimary.TALK) { expanded = !expanded; customizing = false }
            else act { onPrimary(interaction.primary) }
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
            Text(uiText(signal.label), style = MaterialTheme.typography.bodyMedium)
            if (!stopPinned || interaction.primary != HaloOrbPrimary.STOP)
            Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), onClick = { act { onPrimary(interaction.primary) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(primaryLabel)
            }
            if (!stopPinned && interaction.canStop && interaction.primary != HaloOrbPrimary.STOP)
                OutlinedButton(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), onClick = { act(onStop) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Stop")) }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HaloOrbShortcut.entries.filter { it in shortcuts }.forEach { shortcut ->
                    SuggestionChip(onClick = { act { onTools(HaloToolCatalogRequest(shortcut.group, shortcut.entry)) } },
                        label = { Text(stringResource(shortcut.label)) },
                        icon = { HaloIcon(shortcut.icon, null, Modifier.size(20.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            OutlinedButton(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), onClick = { act { onTools(HaloToolCatalogRequest(com.halo.app.ui.screens.home.HomeToolGroup.CREATE)) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.halo_orb_all_tools)) }
            TextButton(onClick = { customizing = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.halo_orb_customize)) }
        }
        TextButton(onClick = ::close, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Close")) }
    }
    Column(Modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (face != null) face(orbClick, orbLabel)
            else HaloSignalFace(signal, modifier = orbModifier, size = size, contentDescription = orbLabel, onClick = orbClick, onClickLabel = orbLabel)
            IconButton(onClick = {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { expanded = !expanded; customizing = false }
            }, modifier = Modifier.size(48.dp)) {
                HaloIcon(HaloGlyph.TOOL, stringResource(R.string.halo_orb_show_shortcuts))
            }
        }
        if (expanded && inline) Surface(shape = com.halo.app.ui.design.HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.width(320.dp).heightIn(max = 440.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) { contents(false) }
        }
    }
    if (expanded && !inline) ModalBottomSheet(onDismissRequest = ::close,
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
