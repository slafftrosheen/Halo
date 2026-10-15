package com.halo.app.ui.screens.home

import com.halo.app.language.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.fx.haloTrace
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import androidx.compose.ui.unit.dp
import com.halo.app.daily.*
import com.halo.app.ui.screens.settings.humanTime
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

@Composable
fun DailyAssistantSection(snapshot: DailyAssistantSnapshot, compact: Boolean, onAction: (DailyAssistantAction) -> Unit) {
    DailyAssistantSection(snapshot, compact, guided = false, onAction = onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyAssistantSection(snapshot: DailyAssistantSnapshot, compact: Boolean, guided: Boolean, onAction: (DailyAssistantAction) -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    LaunchedEffect(compact) { if (compact) showAll = false }
    if (compact) {
        snapshot.primary?.let { item ->
            val study = item.kind in setOf(DailyAssistantKind.STUDY_RESUME, DailyAssistantKind.STUDY_REVIEW)
            val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
            Surface(modifier = Modifier.fillMaxWidth().haloTrace(guided && study,
                MaterialTheme.colorScheme.primary, corner = 20.dp),
                shape = HaloShapes.card, color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = HaloShapes.control, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                HaloIcon(if (study) HaloGlyph.READ_MODE else HaloGlyph.ACTIVITY, null,
                                    Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(uiText(if (study) "Continue" else "Today"),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(item.summary ?: uiText(item.title), style = MaterialTheme.typography.titleMedium)
                        }
                        if (!largeText) DailyPrimaryAction(item, onAction)

                    }
                    if (largeText) DailyPrimaryAction(item, onAction, Modifier.fillMaxWidth())
                    if (snapshot.items.size > 1) TextButton(onClick = { showAll = true },
                        modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text(uiText("See all")) }
                }
            }
        }
    } else {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("Today"), style = MaterialTheme.typography.titleMedium)
                if (snapshot.isCaughtUp) Text(uiText("You're caught up"), style = MaterialTheme.typography.bodyMedium)
                snapshot.primary?.let { DailyItem(it, snapshot, onAction) }
                snapshot.secondary.forEach { DailyItem(it, snapshot, onAction) }
                if (snapshot.items.isEmpty()) Text(uiText("Nothing scheduled soon"), style = MaterialTheme.typography.bodySmall)
                snapshot.companion?.let { state ->
                    TextButton(onClick = { onAction(DailyAssistantAction.OpenDevices) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(state.label) }
                    state.voice?.let { voice -> TextButton(onClick = { onAction(DailyAssistantAction.OpenVoice) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(voice) } }
                }
                TextButton(onClick = { showAll = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("See all")) }
            }
        }
    }
    if (showAll) DailyAssistantSheet(snapshot, { showAll = false }) { action -> showAll = false; onAction(action) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyAssistantSheet(snapshot: DailyAssistantSnapshot, onDismiss: () -> Unit, onAction: (DailyAssistantAction) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Today"), style = MaterialTheme.typography.headlineSmall)
            if (snapshot.isCaughtUp) Text(uiText("You're caught up"))
            val labels = mapOf(DailyAssistantPriority.NEEDS_YOU_NOW to "Needs you", DailyAssistantPriority.DUE to "Due",
                DailyAssistantPriority.SOON to "Next", DailyAssistantPriority.AVAILABLE to "Available")
            labels.forEach { (priority, label) ->
                val rows = snapshot.items.filter { it.priority == priority }
                if (rows.isNotEmpty()) {
                    Text(uiText(label), style = MaterialTheme.typography.titleMedium)
                    rows.forEach { DailyItem(it, snapshot, onAction) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DailyItem(item: DailyAssistantItem, snapshot: DailyAssistantSnapshot, onAction: (DailyAssistantAction) -> Unit) {
    val actionLabel = uiText(dailyActionLabel(item.action))
    val title = uiText(item.title)
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall,
            color = if (item.kind == DailyAssistantKind.AUTOMATION_FAILURE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        item.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (item.kind == DailyAssistantKind.AUTOMATION_UPCOMING) snapshot.nextRoutine?.let {
            Text(humanTime(it.at), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { onAction(item.action) }, modifier = Modifier.heightIn(min = 48.dp).semantics {
            contentDescription = listOfNotNull(title, item.summary, actionLabel).joinToString(". ")
        }) { Text(actionLabel) }
    }
}

fun dailyActionLabel(action: DailyAssistantAction): String = when (action) {
    DailyAssistantAction.OpenInbox -> "Open Inbox"
    is DailyAssistantAction.OpenStudy -> if (action.review) "Review" else "Continue lesson"
    is DailyAssistantAction.OpenAutomation -> "Review automation"
    DailyAssistantAction.OpenLearningSuggestions -> "Review suggestions"
    DailyAssistantAction.OpenDevices -> "Devices"
    DailyAssistantAction.OpenVoice -> "Open Voice"
    is DailyAssistantAction.OpenProvider -> "Set up provider"
    is DailyAssistantAction.OpenActivity -> "Review"
}


@Composable
private fun DailyPrimaryAction(item: DailyAssistantItem, onAction: (DailyAssistantAction) -> Unit,
    modifier: Modifier = Modifier) {
    val study = item.kind in setOf(DailyAssistantKind.STUDY_RESUME, DailyAssistantKind.STUDY_REVIEW)
    Button(onClick = { onAction(item.action) }, shape = HaloShapes.pill,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
        Text(uiText(if (study) "Go" else dailyActionLabel(item.action)))
    }
}
