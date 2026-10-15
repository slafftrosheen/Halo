package com.halo.app.ui.screens.notifications

import com.halo.app.language.uiText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.ui.icons.*
import com.halo.app.ui.design.*
import java.time.Duration
import java.time.Instant

@Composable
internal fun RefreshNotifications(source: NotificationInboxSource) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, source) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) source.refresh() }
        lifecycle.addObserver(observer); source.refresh()
        onDispose { lifecycle.removeObserver(observer) }
    }
}
@Composable
internal fun notificationAge(at: Instant): String {
    val minutes = Duration.between(at, Instant.now()).toMinutes().coerceAtLeast(0)
    return when { minutes < 1 -> uiText("Just now"); minutes < 60 -> uiText("%1\$d min ago", minutes); else -> uiText("%1\$d hr ago", minutes / 60) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationInboxScreen(model: NotificationInboxViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    RefreshNotifications(model.source)
    val state by model.state.collectAsStateWithLifecycle()
    val visible by model.visible.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val app by model.app.collectAsStateWithLifecycle()
    val digest by model.digest.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var filters by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }
    val ownPackage = androidx.compose.ui.platform.LocalContext.current.packageName
    val displayed = visible.filter { row -> when (tab) {
        1 -> row.reply is ReplyCapability.Available
        2 -> row.packageName == ownPackage
        else -> true
    } }
    var digestMenu by remember { mutableStateOf(false) }
    var refine by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            HaloScreenHeader(uiText("Updates"), uiText("Notifications from apps you’ve allowed."), navigation = onBack,
                trailing = {
                    IconButton(onClick = { refine = !refine }, modifier = Modifier.size(48.dp)) {
                        HaloIcon(HaloGlyph.SEARCH, uiText("Search notifications"), Modifier.size(24.dp))
                    }
                })
            HaloSegmentedTabs(listOf(uiText("All"), uiText("Can reply"), uiText("halo")), tab, { tab = it })
            if (!state.connected) {
                HaloFeedbackSurface(uiText("Notification access"),
                    uiText("Choose notification access to see permitted updates in halo. Saved history stays available while disconnected."),
                    action = HaloHeaderAction(uiText("Open notification access"), model.source::openAccess))
            }
            if (refine) {
                Text(uiText("%1\$d notifications · %2\$d available replies", state.items.size,
                    state.items.count { it.reply is ReplyCapability.Available }), style = MaterialTheme.typography.labelMedium)
            Row {
                TextButton(onClick = { filters = true }) { Text(state.items.firstOrNull { it.packageName == app }?.appLabel ?: uiText("All apps")) }
                Box {
                    TextButton(onClick = { digestMenu = true }) { Text(uiText("Digest")) }
                    DropdownMenu(expanded = digestMenu, onDismissRequest = { digestMenu = false }) {
                        listOf(15L to "15 minutes", 60L to "1 hour", 180L to "3 hours", 1440L to "24 hours").forEach { (minutes, label) ->
                            DropdownMenuItem(text = { Text(uiText(label)) }, onClick = { digestMenu = false; model.digest(minutes) })
                        }
                    }
                }
            }
            OutlinedTextField(query, { model.query.value = it }, label = { Text(uiText("Search notifications")) }, singleLine = true, shape = HaloShapes.pill, leadingIcon = { HaloIcon(HaloGlyph.SEARCH, null, Modifier.size(24.dp)) }, modifier = Modifier.fillMaxWidth())
            } else if (query.isNotBlank() || app != null) TextButton(onClick = {
                model.query.value = ""; model.app.value = null
            }) { Text(uiText("Clear filters")) }
        }
        message?.let { item { Text(uiText(it)) } }
        if (displayed.isEmpty()) item {
            HaloEmptyState(uiText(if (query.isNotBlank() || app != null || tab != 0) "No matches" else "Nothing waiting here"),
                uiText(if (query.isNotBlank() || app != null || tab != 0) "Clear the search and app filter to see more notifications." else "Notifications from apps you’ve allowed will appear here."),
                HaloGlyph.CONVERSATION, action = if (query.isNotBlank() || app != null || tab != 0) HaloHeaderAction(uiText("Clear filters")) {
                    model.query.value = ""; model.app.value = null; tab = 0
                } else null)
        }
        items(displayed.sortedByDescending { it.postedAt }, key = { it.key }) { row ->
            NotificationUpdateCard(row, onOpen = { onOpen(row.key) })
        }
        if (displayed.isNotEmpty()) item(key = "end") {
            Text(uiText("That’s everything for this view."),
                modifier = Modifier.padding(vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (filters) ModalBottomSheet(onDismissRequest = { filters = false }) {
        LazyColumn(contentPadding = PaddingValues(20.dp)) {
            item { Text(uiText("Filter by app"), style = MaterialTheme.typography.titleLarge); TextButton(onClick = { model.app.value = null; filters = false }) { Text(uiText("All apps")) } }
            items(state.items.distinctBy { it.packageName }.sortedBy { it.appLabel.lowercase() }) { row ->
                TextButton(onClick = { model.app.value = row.packageName; filters = false }, modifier = Modifier.fillMaxWidth()) { Text(row.appLabel) }
            }
        }
    }
    digest?.let { summary -> ModalBottomSheet(onDismissRequest = { model.digest.value = null }) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(uiText("Notification digest"), style = MaterialTheme.typography.titleLarge); Text(uiText("Local summary · no AI · no cloud")); Text(uiText("%1\$d notifications from %2\$d apps", summary.notificationCount, summary.sourceCount)) }
            items(summary.sections) { section -> Column { Text("${uiText(section.label)} · ${section.count}", style = MaterialTheme.typography.titleMedium); section.sources.forEach { Text(it) } } }
        }
    } }
}


@Composable
private fun NotificationUpdateCard(row: NotificationInboxItem, onOpen: () -> Unit) {
    val glyph = when (row.category) {
        "Calendar" -> HaloGlyph.CALENDAR
        "Calls" -> HaloGlyph.PHONE
        "Media" -> HaloGlyph.SPEAK
        else -> HaloGlyph.CONVERSATION
    }
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = HaloShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = HaloShapes.control, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    HaloIcon(glyph, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.appLabel, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(notificationAge(row.postedAt), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                row.title?.let { Text(it, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis) }
                row.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (row.reply is ReplyCapability.Available) Text(uiText("Reply available"),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
