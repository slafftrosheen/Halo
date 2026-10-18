package com.halo.app.ui.screens.notifications

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.halo.app.ui.fx.HaloPullRing
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
    var digestMenu by remember { mutableStateOf(false) }
    val appliedRefresh by model.source.appliedRefresh.collectAsStateWithLifecycle()
    var requestedRefresh by remember { mutableLongStateOf(appliedRefresh) }
    val pullState = rememberPullToRefreshState()
    val refreshing = notificationRefreshPending(requestedRefresh, appliedRefresh)
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { requestedRefresh = model.source.refresh() },
        modifier = Modifier.fillMaxSize(),
        state = pullState,
        indicator = {
            if (refreshing || pullState.distanceFraction > 0f) {
                HaloPullRing(pullFraction = pullState.distanceFraction,
                    refreshing = refreshing,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter)
                        .padding(top = 8.dp))
            }
        },
    ) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            HaloScreenHeader(uiText("Notifications"), uiText("Private · last 24 hours"), navigation = onBack, glyph = HaloGlyph.CONVERSATION)
            HaloStatusPresentation(if (state.connected) HaloStatus.READY else HaloStatus.UNAVAILABLE,
                label = uiText(if (state.connected) "Connected" else "Not connected"))
            Text(uiText("%1\$d notifications · %2\$d available replies", state.items.size, state.items.count { it.reply is ReplyCapability.Available }),
                style = MaterialTheme.typography.labelMedium)
            if (!state.connected) {
                HaloFeedbackSurface(uiText("Notification access"),
                    uiText("Allow notification access to see permitted notifications in Halo. History remains available while disconnected."),
                    action = HaloHeaderAction(uiText("Open notification access"), model.source::openAccess))
            }
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
            HaloTextField(query, { model.query.value = it }, label = { Text(uiText("Search notifications")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.SEARCH)
        }
        message?.let { item { Text(uiText(it)) } }
        if (visible.isEmpty()) item {
            HaloEmptyState(uiText(if (query.isNotBlank() || app != null) "No matches" else "Nothing waiting here"),
                uiText(if (query.isNotBlank() || app != null) "Clear the search and app filter to see more notifications." else "Notifications from apps you’ve allowed will appear here."),
                HaloGlyph.CONVERSATION, action = if (query.isNotBlank() || app != null) HaloHeaderAction(uiText("Clear filters")) {
                    model.query.value = ""; model.app.value = null
                } else null)
        }
        listOf("Messages", "Calendar", "Calls", "Media", "Other").forEach { category ->
            val rows = visible.filter { it.category == category }.sortedByDescending { it.postedAt }
            if (rows.isNotEmpty()) {
                item(key = "category:$category") { Text(uiText(category), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(rows, key = { it.key }) { row ->
                    val openLabel = uiText("Open notification from %1\$s", row.appLabel)
                    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = openLabel) { onOpen(row.key) }.padding(vertical = 12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HaloIcon(HaloGlyph.CONVERSATION, contentDescription = null, modifier = Modifier.size(24.dp))
                            Text(row.appLabel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text(notificationAge(row.postedAt), style = MaterialTheme.typography.labelSmall)
                        }
                        row.title?.let { Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        row.body?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) }
                        Text(uiText(if (row.active) "Active" else "Ended"), style = MaterialTheme.typography.labelSmall)
                        (row.reply as? ReplyCapability.Available)?.let { Text(uiText("%1\$s available", it.label), style = MaterialTheme.typography.labelMedium) }
                    }
                    HorizontalDivider()
                }
            }
        }
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
